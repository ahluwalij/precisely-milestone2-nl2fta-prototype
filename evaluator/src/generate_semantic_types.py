#!/usr/bin/env python3
"""NL2FTA semantic type generator.

This script connects to a backend, configures AWS for Bedrock usage, constructs
description prompts (P1..P6) from dataset inputs, invokes the
"/api/semantic-types/generate" endpoint, and writes results in the canonical
format expected by the evaluator under evaluator/generated_semantic_types/.

Output file format example:
{
  "dataset": "insurance",
  "description_number": 6,
  "timestamp": "20250808_104439",
  "generated_types": [ ... ]
}
"""

from __future__ import annotations

import argparse
import datetime as _dt
import json
import os
import random
import re
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Tuple
import csv
from concurrent.futures import ThreadPoolExecutor, as_completed

import requests

import pandas as pd  # type: ignore


EVALUATOR_DIR = Path(__file__).parent.parent
GENERATED_TYPES_DIR = EVALUATOR_DIR / "generated_semantic_types"
GEN_INPUTS_DIR = EVALUATOR_DIR / "datasets" / "generation-inputs"


def _now_ts() -> str:
    """Return an evaluation-friendly timestamp string."""
    return _dt.datetime.now().strftime("%Y%m%d_%H%M%S")


def _read_csvs(data_dir: Optional[str], files_csv: Optional[str]) -> List[Path]:
    """Collect CSV paths from a directory or a comma-separated list.

    Args:
      data_dir: An optional directory to scan recursively for CSVs.
      files_csv: An optional comma-separated list of files/dirs.

    Returns:
      A list of Paths to CSV files.
    """
    csvs: List[Path] = []
    if files_csv:
        for token in [t.strip() for t in files_csv.split(',') if t.strip()]:
            p = Path(token).expanduser().resolve()
            if p.is_dir():
                csvs.extend(sorted(p.rglob("*.csv")))
            elif p.suffix.lower() == ".csv" and p.exists():
                csvs.append(p)
    if data_dir and not csvs:
        base = Path(data_dir).expanduser().resolve()
        if base.is_dir():
            csvs.extend(sorted(base.rglob("*.csv")))
    return csvs


def _sample_examples_from_csv(csv_path: Path, max_rows: int = 2000, seed: int = 13) -> Tuple[List[str], List[str], List[str]]:
    """Extract candidate column names and sample values from a CSV.

    The generator needs at least a header name and a short description scaffold; we use sampled
    values to craft description text that the backend will enrich.

    Returns:
      (column_names, positive_value_samples, header_name_samples)
    """
    random.seed(seed)
    try:
        if pd is None:
            return ([], [], [])
        df = pd.read_csv(csv_path, nrows=max_rows)
        columns = [str(c) for c in df.columns.tolist()]
        header_samples = random.sample(columns, k=min(5, len(columns))) if columns else []
        # take up to 100 non-null stringified values from first few columns
        value_samples: List[str] = []
        for col in columns[: min(4, len(columns))]:
            series = df[col].dropna().astype(str).head(200)
            for v in series.tolist():
                s = v.strip()
                if s and len(s) <= 64:
                    value_samples.append(s)
                if len(value_samples) >= 50:
                    break
            if len(value_samples) >= 50:
                break
        return (columns, value_samples[:30], header_samples)
    except Exception as e:
        raise RuntimeError(f"Failed to extract samples from {csv_path}: {e}")


def _p_description(idx: int, column_name: str, sample_values: List[str]) -> str:
    """Compose a short description for P1..P6 variants.

    This mirrors the meaning in evaluator/README. The backend prompt builder will augment heavily.
    """
    examples = ", ".join(sample_values[:2]) if sample_values else ""
    cn = column_name or "field"
    if idx == 1:
        return f"Business glossary entry for {cn}. Two typical examples: {examples}."
    if idx == 2:
        return f"Values describing {cn} with a consistent shape and strict formatting; no regex included."
    if idx == 3:
        return f"Specific format for {cn} with two examples {examples}; avoid regex details."
    if idx == 4:
        return f"Specific format for {cn} with two examples {examples}; includes a short regex hint."
    if idx == 5:
        return f"Short description for {cn}, header name not exact, informal pattern guidance; no regex or examples."
    if idx == 6:
        return f"Short description for {cn}, informal pattern guidance with two pos and two neg examples."
    return f"Short description for {cn}."


def _build_generation_request(description_text: str, column_header: str, values: List[str], desc_idx: int) -> Dict[str, Any]:
    # Minimize optional fields to encourage backend to generate suitable examples
    pos_vals = values[:6]
    neg_vals: List[str] = []
    if desc_idx in (3, 4, 6) and values:
        # fabricate near-miss negatives by simple perturbations
        for v in values[:4]:
            if v.isdigit():
                neg_vals.append(v + "0")
            elif re.search(r"[A-Za-z]", v):
                neg_vals.append(v + "_")
    payload: Dict[str, Any] = {
        "typeName": None,
        "description": description_text,
        "positiveContentExamples": pos_vals,
        "negativeContentExamples": neg_vals,
        "positiveHeaderExamples": [column_header] if column_header else [],
        "negativeHeaderExamples": [],
        "checkExistingTypes": True,
        "proceedDespiteSimilarity": False,
        "generateExamplesForExistingType": None,
        "columnHeader": column_header,
    }
    return payload


def _ensure_aws_configured(api_base: str, region: str) -> None:
    """Configure AWS creds in backend using the semantic-types generation controller.

    Idempotent: safe to call multiple times.
    """
    ak = os.environ.get("AWS_ACCESS_KEY_ID", "").strip()
    sk = os.environ.get("AWS_SECRET_ACCESS_KEY", "").strip()
    if not ak or not sk:
        raise RuntimeError("AWS credentials are required in environment")
    url = f"{api_base}/semantic-types/aws/configure"
    body = {"accessKeyId": ak, "secretAccessKey": sk, "region": region}
    r = requests.post(url, json=body, timeout=60)
    if r.status_code != 200:
        raise RuntimeError(f"AWS configure failed: {r.status_code} {r.text}")


def _generate_type(api_base: str, payload: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    url = f"{api_base}/semantic-types/generate"
    r = requests.post(url, json=payload, timeout=120)
    if r.status_code != 200:
        return {"resultType": "error", "explanation": f"HTTP {r.status_code}", "raw": r.text}
    try:
        data = r.json()
        # Backend returns either full GeneratedSemanticType or existing match info
        if isinstance(data, dict):
            return data
        raise ValueError("Unexpected response type from backend (expected dict or JSON string)")
    except Exception as e:  # noqa: BLE001
        raise RuntimeError(f"JSON parse error: {e}") from e


def _generate_type_with_retry(api_base: str, payload: Dict[str, Any], max_retries: int = 3) -> Optional[Dict[str, Any]]:
    """Generate type with retry logic for parallel processing."""
    for attempt in range(max_retries):
        try:
            return _generate_type(api_base, payload)
        except Exception as e:
            if attempt == max_retries - 1:  # Last attempt
                print(f"Failed to generate type after {max_retries} attempts: {e}")
                return {"resultType": "error", "explanation": str(e)}
            # Wait before retry (exponential backoff)
            import time
            time.sleep(2 ** attempt)
    return None


def _process_row_parallel(api_base: str, row: Dict[str, str], desc_idx: int, basename: str) -> Tuple[int, Dict[str, Any]]:
    """Process a single row for a specific description index. Returns (desc_idx, result)."""
    label_type = (row.get("Type", "") or "").strip()
    column_name = (row.get("Column_Name", "") or "").strip()

    desc_col_dynamic = f"Generation Description {desc_idx}"
    description_text = row.get(desc_col_dynamic, "") or ""
    description_text = description_text.strip() if isinstance(description_text, str) else ""
    if not description_text:
        return desc_idx, None

    pos_vals: List[str] = []
    neg_vals: List[str] = []
    if desc_idx in (3, 4, 6):
        pos_vals = _split_examples(row.get("Positive Value Examples"))
        neg_vals = _split_examples(row.get("Negative Value Examples"))

    pos_headers = _split_examples(row.get("Positive Header Examples"))
    neg_headers = _split_examples(row.get("Negative Header Examples"))

    payload = {
        "typeName": label_type if label_type else None,
        "description": description_text,
        "positiveContentExamples": pos_vals,
        "negativeContentExamples": neg_vals,
        "positiveHeaderExamples": pos_headers or ([column_name] if column_name else []),
        "negativeHeaderExamples": neg_headers,
        "checkExistingTypes": True,
        "proceedDespiteSimilarity": False,
        "generateExamplesForExistingType": None,
        "columnHeader": column_name,
    }

    result = _generate_type_with_retry(api_base, payload)
    if result is not None:
        if label_type:
            result["semanticType"] = label_type
        try:
            name = label_type or result.get("semanticType") or result.get("existingTypeMatch") or "unknown"
            rtype = result.get("resultType", "unknown")
            # Format output to avoid GitHub Actions masking
            status = "generated" if rtype == "generated" else rtype
            safe_name = name.replace("*", "STAR").replace("-", "DASH")  # Avoid masking patterns
            print(f"[{status}] Generated type {safe_name} for {basename} (desc {desc_idx})")
        except Exception as e:
            print(f"Failed to process generated result: {e}")

    return desc_idx, result


def _derive_output_basename(
    data_dir: Optional[str], files_csv: Optional[str], dataset_tag: Optional[str] = None
) -> str:
    """Use the data file's basename (without extension) as output basename.

    When multiple files are provided, use the first file's base name.
    If a directory is given, attempt to find a *_data.csv under generation-labels and
    mirror its base name; otherwise use the directory name.
    """
    if files_csv:
        first = [t.strip() for t in files_csv.split(',') if t.strip()]
        if first:
            p = Path(first[0]).expanduser().resolve()
            if p.is_file():
                return p.stem
            if p.is_dir():
                # try to locate a *_data.csv inside
                for candidate in sorted(p.glob("*_data.csv")):
                    return candidate.stem
                return p.name
    if data_dir:
        base = Path(data_dir).expanduser().resolve()
        if base.is_dir():
            # auxiliary mapping via generation-labels: if a matching *_data.csv exists, use it
            for candidate in sorted(base.glob("*_data.csv")):
                return candidate.stem
            return base.name
    # last resort
    if dataset_tag and dataset_tag.strip():
        return dataset_tag.strip()
    return "dataset"


def _write_output(basename: str, description_idx: int, run_ts: str, results: List[Dict[str, Any]]) -> Path:
    out = {
        "dataset": basename,
        "description_number": description_idx,
        "timestamp": run_ts,
        "generated_types": results,
    }
    out_name = f"{basename}_description{description_idx}_{run_ts}.json"
    out_path = GENERATED_TYPES_DIR / out_name
    GENERATED_TYPES_DIR.mkdir(parents=True, exist_ok=True)
    tmp_path = out_path.with_suffix(".json.tmp")
    with tmp_path.open("w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    tmp_path.replace(out_path)
    return out_path


def _collect_candidate_columns(csvs: List[Path], limit_columns: int = 24) -> List[Tuple[str, List[str]]]:
    """Return list of (column_name, value_samples) pairs aggregated across CSVs."""
    seen: Dict[str, List[str]] = {}
    for csv_path in csvs[:10]:  # limit files per run to keep prompt counts reasonable
        cols, values, _headers = _sample_examples_from_csv(csv_path)
        for c in cols:
            if c not in seen:
                seen[c] = []
        # stash values under a synthetic key for potential reuse when column exists
        if values:
            key = cols[0] if cols else "__values__"
            seen.setdefault(key, [])
            if len(seen[key]) < 60:
                seen[key].extend(values)
    pairs: List[Tuple[str, List[str]]] = []
    for name in list(seen.keys()):
        if name.startswith("__"):  # skip synthetic keys
            continue
        samples = seen.get(name, []) or seen.get("__values__", [])
        pairs.append((name, samples))
        if len(pairs) >= limit_columns:
            break
    return pairs


def _inputs_path_for_name(name: str) -> Optional[Path]:
    """Return generation inputs CSV path for the dataset name.

    Maps <name> -> generation-inputs/<name>_inputs.csv
    """
    n = name[:-5] if name.endswith("_data") else name
    candidate = GEN_INPUTS_DIR / f"{n}_inputs.csv"
    return candidate if candidate.exists() else None


def _iter_input_rows(inputs_csv: Path) -> Iterable[Dict[str, str]]:
    with inputs_csv.open(newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            # Normalize keys by stripping whitespace
            normalized = {k.strip(): (v.strip() if isinstance(v, str) else v) for k, v in row.items()}
            yield normalized


def _available_description_indices(inputs_csv: Path) -> List[int]:
    """Return list of description indices present in the inputs CSV header.

    Looks for columns named 'Generation Description <n>' and returns [n,...].
    """
    try:
        with inputs_csv.open(newline="", encoding="utf-8") as f:
            reader = csv.reader(f)
            header = next(reader, [])
            out: List[int] = []
            for h in header:
                if not isinstance(h, str):
                    continue
                m = re.match(r"\s*Generation\s+Description\s+(\d+)\s*$", h)
                if m:
                    try:
                        out.append(int(m.group(1)))
                    except Exception:
                        pass
            # Stable order
            return sorted(set(out))
    except Exception:
        return []


def _split_examples(value: Optional[str]) -> List[str]:
    if not value:
        return []
    # Examples are semicolon-separated; trim and drop empties
    return [s.strip() for s in value.split(";") if s and s.strip()]


def _generate_for_dataset(
    dataset_name: str,
    descriptions: List[int],
    api_base: str,
    run_ts: str,
    data_dir: Optional[str],
    files_csv: Optional[str],
    region: str,
) -> None:
    # Configure AWS in backend (idempotent)
    _ensure_aws_configured(api_base, region)

    # Prefer inputs CSV
    inputs_csv = _inputs_path_for_name(dataset_name)
    basename = dataset_name
    if inputs_csv and inputs_csv.exists():
        rows = list(_iter_input_rows(inputs_csv))
        descs_from_header = _available_description_indices(inputs_csv)
        # If user explicitly provided descriptions, use those; otherwise auto-detect from header
        to_run = descriptions if descriptions else descs_from_header
        print(f"Using generation inputs: {inputs_csv}")
        if descriptions:
            print(f"Using provided descriptions: {' '.join(str(d) for d in to_run)}")
        elif descs_from_header:
            print(f"Auto-detected descriptions from inputs: {' '.join(str(d) for d in to_run)}")

        # Use parallel processing with ThreadPoolExecutor
        # Process ALL description-row combinations in parallel for maximum speed
        max_workers = min(40, len(to_run) * len(rows))  # Cap at 40 workers as requested

        # Group results by description
        results_by_desc: Dict[int, List[Dict[str, Any]]] = {desc_idx: [] for desc_idx in to_run}

        # Submit ALL tasks (all descriptions × all rows) to thread pool at once
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            # Submit tasks for ALL description-row combinations
            future_to_task = {}
            for desc_idx in to_run:
                for row in rows:
                    future = executor.submit(_process_row_parallel, api_base, row, desc_idx, basename)
                    future_to_task[future] = desc_idx

            # Collect results as they complete
            for future in as_completed(future_to_task):
                try:
                    desc_idx_result, result = future.result()
                    if result is not None:
                        results_by_desc[desc_idx_result].append(result)
                except Exception as e:
                    print(f"Error processing row: {e}")

        # Write results for each description
        for desc_idx in to_run:
            results = results_by_desc[desc_idx]
            if results:
                out_path = _write_output(basename, desc_idx, run_ts, results)
                print(f"✔ Wrote {out_path}")
            else:
                print(f"Skipping description {desc_idx} for {basename} (no inputs/outputs).")
        return

    # Fallback flow (no inputs CSV): only allowed when explicit data_dir/files are provided
    if not (data_dir or files_csv):
        raise SystemExit(
            f"No generation inputs found for '{dataset_name}'. "
            f"Expected CSV at {GEN_INPUTS_DIR}/{dataset_name}_inputs.csv or pass --data-dir/--files."
        )

    csvs = _read_csvs(data_dir, files_csv)
    if not csvs:
        raise SystemExit(
            f"No CSVs discovered from --data-dir/--files for '{dataset_name}'. Aborting."
        )
    basename = _derive_output_basename(data_dir, files_csv, dataset_name)
    candidates = _collect_candidate_columns(csvs)
    if not candidates:
        raise SystemExit(
            f"Could not extract candidate columns from provided CSVs for '{dataset_name}'. Aborting."
        )

    # Use parallel processing for fallback flow as well
    max_workers = min(40, len(descriptions) * len(candidates))  # Cap at 40 workers

    # Group results by description for fallback flow
    results_by_desc: Dict[int, List[Dict[str, Any]]] = {desc_idx: [] for desc_idx in descriptions}

    # Submit ALL tasks (all descriptions × all candidates) to thread pool at once
    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        future_to_task = {}
        for desc_idx in descriptions:
            for (header, value_samples) in candidates:
                description_text = _p_description(desc_idx, header, value_samples)
                payload = _build_generation_request(description_text, header, value_samples, desc_idx)
                future = executor.submit(_generate_type_with_retry, api_base, payload)
                future_to_task[future] = (desc_idx, header, value_samples)

        # Collect results as they complete
        for future in as_completed(future_to_task):
            try:
                desc_idx, header, value_samples = future_to_task[future]
                result = future.result()
                if result is not None:
                    results_by_desc[desc_idx].append(result)
                    try:
                        name = (
                            (result.get("semanticType") if isinstance(result, dict) else None)
                            or (result.get("existingTypeMatch") if isinstance(result, dict) else None)
                            or "unknown"
                        )
                        rtype = result.get("resultType", "unknown") if isinstance(result, dict) else "unknown"
                        # Format output to avoid GitHub Actions masking
                        status = "generated" if rtype == "generated" else rtype
                        safe_name = name.replace("*", "STAR").replace("-", "DASH")  # Avoid masking patterns
                        print(f"[{status}] Generated type {safe_name} for {basename} (desc {desc_idx})")
                    except Exception as e:
                        print(f"Failed to process generated result: {e}")
            except Exception as e:
                print(f"Error processing candidate: {e}")

    # Write results for each description
    for desc_idx in descriptions:
        results = results_by_desc[desc_idx]
        if results:
            out_path = _write_output(basename, desc_idx, run_ts, results)
            # Avoid masking by using a different format for the success message
            safe_path = str(out_path).replace("***", "TIMESTAMP")
            print(f"SUCCESS: Wrote {safe_path}")
        else:
            print(f"Skipping description {desc_idx} for {basename} (no outputs).")


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate semantic types via backend API")
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--descriptions", required=False, default="", help="space-separated list of description numbers (optional; if omitted, auto-detect from inputs header)")
    parser.add_argument("--api-base-url", required=True)
    parser.add_argument("--data-dir")
    parser.add_argument("--files")
    parser.add_argument("--region", default=os.environ.get("AWS_DEFAULT_REGION", "us-east-1"))
    parser.add_argument("--run-timestamp", default=_now_ts())
    args = parser.parse_args()

    api_base = args.api_base_url.rstrip('/')
    descriptions: List[int] = [int(x) for x in args.descriptions.split() if x.strip().isdigit()]
    # Determine which dataset names to generate for
    ds_raw = (args.dataset or "all").strip()
    if ds_raw.lower() == "all":
        # enumerate all *_inputs.csv under generation-inputs/
        names: List[str] = []
        if GEN_INPUTS_DIR.exists():
            for p in sorted(GEN_INPUTS_DIR.glob("*_inputs.csv")):
                names.append(p.stem[:-7])  # remove _inputs suffix
        if not names:
            raise SystemExit(
                f"No generation-inputs found in {GEN_INPUTS_DIR}. "
                f"Add <name>_inputs.csv or pass --dataset with --data-dir/--files."
            )
    else:
        # Support comma/space separated list; avoid duplicate names
        tokens = [tok.strip() for tok in ds_raw.replace(',', ' ').split(' ') if tok.strip()]
        names: List[str] = []
        seen: set[str] = set()
        for t in tokens:
            if t not in seen:
                names.append(t)
                seen.add(t)

    if not names:
        raise SystemExit("No dataset names resolved. Aborting.")

    # Validate requested datasets have inputs unless caller provides explicit data/files
    if ds_raw.lower() != "all" and not (args.data_dir or args.files):
        missing: List[str] = []
        for n in names:
            if _inputs_path_for_name(n) is None:
                missing.append(n)
        if missing:
            pretty = ", ".join(missing)
            raise SystemExit(
                f"Missing generation-inputs for: {pretty}. "
                f"Expected {GEN_INPUTS_DIR}/<name>_inputs.csv or pass --data-dir/--files."
            )

    for name in names:
        _generate_for_dataset(
            dataset_name=name,
            descriptions=descriptions,
            api_base=api_base,
            run_ts=args.run_timestamp,
            data_dir=args.data_dir,
            files_csv=args.files,
            region=args.region,
        )


if __name__ == "__main__":
    main()


