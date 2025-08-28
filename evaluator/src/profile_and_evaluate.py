#!/usr/bin/env python3
"""Profile and evaluate generated semantic types.

This module loads generated semantic types, profiles data against the backend
API, and calculates evaluation metrics. Functionality preserved; edits focus on
style and clarity.
"""

import argparse
import glob
import json
import logging
import os
import re
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional, Set, Tuple
from time import time, sleep
import math

from tqdm import tqdm
import pandas as pd
import requests
#

# API Configuration: allow override by env var or CLI
DEFAULT_BASE_URL = "http://localhost:8081/api"
BASE_URL = os.environ.get("EVALUATOR_API_BASE_URL", DEFAULT_BASE_URL)

# File paths - use relative paths from the evaluator directory
EVALUATOR_DIR = Path(__file__).parent.parent
DATASETS_DIR = EVALUATOR_DIR / "datasets" / "data"
GENERATED_TYPES_DIR = EVALUATOR_DIR / "generated_semantic_types"
_SUFFIXES_TO_STRIP = ("_data",)
"""Directory layout rules.

- If env EVALUATOR_RUN_DIR is set, use it as the per-run directory directly.
- Otherwise, use evaluator/logs/<RUN_TIMESTAMP> as the per-run directory.
"""
_ENV_RUN_DIR = os.environ.get("EVALUATOR_RUN_DIR")
RUN_TIMESTAMP = os.environ.get("EVAL_RUN_TIMESTAMP", datetime.now().strftime("%Y%m%d_%H%M%S"))
if _ENV_RUN_DIR:
    RUN_DIR = Path(_ENV_RUN_DIR)
    LOGS_DIR = RUN_DIR
else:
    RUN_DIR = EVALUATOR_DIR / "logs" / RUN_TIMESTAMP
    LOGS_DIR = RUN_DIR

# Setup logging
# Ensure directories exist
RUN_DIR.mkdir(parents=True, exist_ok=True)
logging.basicConfig(
    level=logging.DEBUG,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[logging.StreamHandler()]
)
logger = logging.getLogger(__name__)


def _is_eval_mode() -> bool:
    return os.environ.get("EVAL_MODE", "").strip().lower() in ("1", "true", "yes", "on")


def _eval_get_int(var_name: str, eval_default: int, fallback_default: int) -> int:
    raw = os.environ.get(var_name)
    if raw is not None and str(raw).strip() != "":
        try:
            return int(str(raw).strip())
        except ValueError:
            return eval_default
    return eval_default if _is_eval_mode() else fallback_default


def _eval_get_bool(var_name: str, eval_default: bool, fallback_default: bool) -> bool:
    raw = os.environ.get(var_name)
    if raw is not None:
        return str(raw).strip().lower() in ("1", "true", "yes", "on")
    return eval_default if _is_eval_mode() else fallback_default


class ProfileEvaluator:
    """Evaluator for profiling data with generated semantic types."""
    
    dataset: str
    session: requests.Session
    base_url: str
    data_file: Path
    labels_file: Path
    ground_truth: Dict[str, str]

    _created_types: Set[str]
    _existing_types_cache: Dict[str, Dict]
    _only_types_regex: Optional[str]
    _dry_run_create: bool
    _headers_gate: bool
    _baseline_mode: bool
    _headers_row_index: Optional[int]
    _gt_baseline: Optional[Dict[str, str]]
    _gt_custom: Optional[Dict[str, str]]

    def __init__(
        self,
        dataset: str = "extension",
        base_url: Optional[str] = None,
        full_data: bool = False,
        files: Optional[List[str]] = None,
        data_dir: Optional[str] = None,
        parallel: bool = True,
        max_workers: Optional[int] = None,
    ):
        """Initialize the profiler.

        Args:
            dataset: The dataset name to use.
            base_url: Optional override for API base URL.
            full_data: Disable truncation safeguards when True.
            files: Optional list of files/dirs for multi-file evaluation.
            data_dir: Optional base directory for resolving files.
            parallel: Enable parallel per-file profiling.
            max_workers: Optional override for thread pool size.
        """
        # Derive dataset name from provided directory/files to avoid hidden defaults
        derived_dataset = dataset
        if data_dir:
            try:
                derived_dataset = Path(data_dir).name or dataset
            except Exception:
                pass
        elif files:
            try:
                first = Path(files[0])
                if first.is_dir():
                    derived_dataset = first.name
                else:
                    derived_dataset = first.parent.name or dataset
            except Exception:
                pass
        self.dataset = derived_dataset
        # Build candidate tags from inputs to allow dynamic mapping by file name and parent dir
        self._dataset_candidates: List[str] = []
        self.session = requests.Session()
        # Resolve base URL precedence: explicit arg > env var > default
        self.base_url = (base_url or os.environ.get("EVALUATOR_API_BASE_URL") or DEFAULT_BASE_URL)
        
        # Track types created by this evaluation session
        self._created_types = set()
        self._existing_types_cache = {}
        self._only_types_regex = None
        self._dry_run_create = False
        self._headers_gate = False  # If True: headers act as a gate (any header must match)
        self._baseline_mode = False  # Internal flag: prefer baseline GT when available
        self._headers_row_index = None
        self._gt_baseline = None
        self._gt_custom = None
        self._full_data = full_data  # CLI override to disable truncation
        self._parallel = parallel
        self._max_workers = max_workers  # None => ThreadPoolExecutor default (min(32, os.cpu_count()+4))
        
        # Resolve multi-file inputs (new universal flow)
        base_dir = Path(data_dir) if data_dir else None
        self.input_files = self._resolve_inputs(files or [], base_dir)
        if not self.input_files:
            raise FileNotFoundError("No input files found. Provide --data-dir or --files (containing at least one directory or CSV file).")
        logger.info(f"Initialized profiler for dataset: {self.dataset} with {len(self.input_files)} input file(s)")

        # Derive candidate tags from file basenames and parent directories
        candidates: List[str] = [self.dataset]
        for p in self.input_files:
            try:
                stem = Path(p).stem
                for suf in _SUFFIXES_TO_STRIP:
                    if stem.endswith(suf):
                        stem = stem[: -len(suf)]
                        break
                parent = Path(p).parent.name
                if stem and stem not in candidates:
                    candidates.append(stem)
                if parent and parent not in candidates:
                    candidates.append(parent)
            except Exception:
                continue
        # Preserve order, ensure uniqueness
        seen: Set[str] = set()
        uniq: List[str] = []
        for c in candidates:
            if c and c not in seen:
                uniq.append(c)
                seen.add(c)
        self._dataset_candidates = uniq

    def _load_universal(
        self, file: Path
    ) -> Tuple[List[str], List[Dict[str, Optional[str]]], Dict[str, str], Dict[str, str]]:
        """Strict universal parser.

        Layout: row0=baseline GT, row1=custom GT, row2=headers, row3+=data.

        Returns:
            headers, data_rows, gt_base, gt_custom.
        """
        df = pd.read_csv(file, header=None, dtype=str, low_memory=False)
        if df.shape[0] < 4:
            raise ValueError(f"File {file} does not have at least 4 rows for universal format")
        headers_row = df.iloc[2].tolist()
        baseline_row = df.iloc[0].tolist()
        custom_row = df.iloc[1].tolist()
        gt_base: Dict[str, str] = {}
        gt_custom: Dict[str, str] = {}
        headers: List[str] = []
        for idx, h in enumerate(headers_row):
            name = str(h) if h is not None else ''
            if not name or name.lower() == 'nan':
                continue
            headers.append(name)
            # Baseline
            if idx < len(baseline_row):
                t0 = str(baseline_row[idx])
                if t0 and t0.lower() != 'nan' and t0.strip() != '':
                    if any(sfx in t0 for sfx in ('.1', '.2', '.3')):
                        t0 = t0.split('.')[0]
                    gt_base[name] = t0
            # Custom
            if idx < len(custom_row):
                t1 = str(custom_row[idx])
                if t1 and t1.lower() != 'nan' and t1.strip() != '':
                    if any(sfx in t1 for sfx in ('.1', '.2', '.3')):
                        t1 = t1.split('.')[0]
                    gt_custom[name] = t1
        # Build data rows
        data_df = df.iloc[3:].reset_index(drop=True)
        data_rows: List[Dict[str, Optional[str]]] = []
        for _, row in data_df.iterrows():
            d: Dict[str, Optional[str]] = {}
            for i, col in enumerate(headers):
                val = row[i] if i < len(row) else None
                s = None if val is None else str(val)
                if s is None or s.strip() == '' or s.lower() == 'nan':
                    d[col] = None
                else:
                    d[col] = s
            data_rows.append(d)
        return headers, data_rows, gt_base, gt_custom

    def profile_rows(self, columns: List[str], data_rows: List[Dict[str, Optional[str]]], custom_only: bool = False) -> Dict:
        logger.debug(f"Profiling {len(columns)} columns with {len(data_rows)} rows")
        try:
            actual_headers = list(columns)
            if not self._full_data:
                max_cols_cap = _eval_get_int("EVAL_MAX_COLUMNS", eval_default=300, fallback_default=300)
                if len(actual_headers) > max_cols_cap:
                    actual_headers = actual_headers[:max_cols_cap]
                    data_rows = [
                        {k: v for k, v in row.items() if k in set(actual_headers)}
                        for row in data_rows
                    ]
                    logger.info(f"EVAL_MAX_COLUMNS cap applied: columns={len(actual_headers)}")

            if self._full_data:
                should_truncate_flag = False
            else:
                should_truncate_flag = _eval_get_bool("EVAL_TRUNCATE_DATA", eval_default=True, fallback_default=True)
            should_truncate = should_truncate_flag
            if should_truncate:
                original_rows = len(data_rows)
                row_cap = _eval_get_int("EVAL_MAX_ROWS", eval_default=1000, fallback_default=1000)
                data_rows = data_rows[:row_cap]

            request = {
                "table_name": f"{self.dataset}_table",
                "columns": actual_headers,
                "data": data_rows,
                "max_samples": len(data_rows),
                "include_statistics": False,
                "custom_only": custom_only
            }
            logger.debug(f"Profiling request: {len(actual_headers)} columns, {len(data_rows)} rows, custom_only={custom_only}")
            response = requests.post(
                f"{BASE_URL}/classify/table",
                json=request,
                timeout=300
            )
            logger.debug(f"Received response with status code: {response.status_code}")
            if response.status_code == 200:
                logger.debug("Data profiled successfully.")
                return response.json()
            else:
                logger.error(f"Profiling failed: {response.status_code}")
                logger.error(f"Response text: {response.text[:500]}")
                return {"columns": [], "column_classifications": {}}
        except requests.exceptions.Timeout:
            logger.error("Profiling request timed out (5 minutes).")
            return {}
        except Exception as e:
            logger.error(f"Error profiling data: {e}")
            return {}

    def _aggregate_metrics_list(self, metrics_list: List[Dict]) -> Dict:
        if not metrics_list:
            return {
                "accuracy": 0, "precision": 0, "recall": 0, "f1_score": 0,
                "total_columns": 0, "excluded_columns": 0,
                "correct_predictions": 0, "true_positives": 0, "false_positives": 0, "false_negatives": 0,
                "details": []
            }
        sums = {
            "correct_predictions": 0, "true_positives": 0, "false_positives": 0, "false_negatives": 0,
            "total_columns": 0, "excluded_columns": 0
        }
        details: List[Dict] = []
        for m in metrics_list:
            for k in sums:
                sums[k] += int(m.get(k, 0))
            if m.get("details"):
                details.extend(m["details"])
        correct = sums["correct_predictions"]
        tp = sums["true_positives"]
        fp = sums["false_positives"]
        fn = sums["false_negatives"]
        total = sums["total_columns"]
        accuracy = correct / total if total > 0 else 0
        precision = tp / (tp + fp) if (tp + fp) > 0 else 0
        recall = tp / (tp + fn) if (tp + fn) > 0 else 0
        f1 = 2 * (precision * recall) / (precision + recall) if (precision + recall) > 0 else 0
        return {
            "accuracy": accuracy, "precision": precision, "recall": recall, "f1_score": f1,
            **sums, "details": details
        }

    def _resolve_inputs(self, raw_inputs: List[str], base_dir: Optional[Path]) -> List[Path]:
        resolved: List[Path] = []
        if not raw_inputs and base_dir and base_dir.is_dir():
            # Default to scanning the provided base directory
            resolved.extend(sorted([p for p in base_dir.glob('**/*.csv') if p.is_file()]))
            return resolved
        for item in raw_inputs:
            p = Path(item)
            candidates: List[Path] = []
            if p.is_absolute():
                candidates.append(p)
            elif base_dir:
                candidates.append(base_dir / item)
                candidates.append(Path.cwd() / item)
            else:
                candidates.append(Path.cwd() / item)
            for cand in candidates:
                if cand.is_dir():
                    resolved.extend(sorted([q for q in cand.glob('**/*.csv') if q.is_file()]))
                elif cand.is_file():
                    resolved.append(cand)
        # Deduplicate
        seen = set()
        uniq: List[Path] = []
        for f in resolved:
            if f not in seen:
                uniq.append(f)
                seen.add(f)
        return uniq

    def _profile_and_score(self, file_path: Path, custom_only: bool) -> Dict:
        logger.info(f"Profiling file: {file_path}")
        headers, rows, gt_base, gt_custom = self._load_universal(file_path)
        preds = self.profile_rows(headers, rows, custom_only=custom_only)
        gt = gt_custom if custom_only else gt_base
        results_detail, excluded_columns = self._compute_classification_details(self._parse_predictions(preds), gt)
        logger.info(f"Finished profiling {file_path}")
        return self._calculate_metric_scores(results_detail, len(excluded_columns))

    def _per_column_correct_map(self, file_path: Path, custom_only: bool) -> Dict[str, Dict[str, Any]]:
        """Return a map of column -> {'correct': bool, 'semantic_type': str} for a single file.

        Uses baseline ground truth (row0) when custom_only is False, and custom ground truth (row1)
        when custom_only is True.
        """
        headers, rows, gt_base, gt_custom = self._load_universal(file_path)
        predictions = self.profile_rows(headers, rows, custom_only=custom_only)
        classifications = self._parse_predictions(predictions)
        ground_truth = gt_custom if custom_only else gt_base
        outcome: Dict[str, Dict[str, Any]] = {}
        for col_name, classification in classifications.items():
            true_type = ground_truth.get(col_name)
            if not true_type or true_type == 'nan':
                # Skip columns without GT
                continue
            predicted_type = classification.get('semantic_type') or classification.get('semanticType') or 'NONE'
            outcome[col_name] = {
                'correct': (predicted_type == true_type),
                'semantic_type': predicted_type
            }
        return outcome
    
    def load_ground_truth(self) -> Dict[str, str]:
        """Strict universal GT: row0=baseline, row1=custom, row2=headers, row3+=data.

        Returns the active GT map based on mode:
        - baseline mode (self._baseline_mode=True): returns baseline (row0)
        - custom mode: returns custom (row1)
        No fallbacks to labels or legacy layouts.
        """
        data_df = pd.read_csv(self.data_file, header=None, dtype=str, low_memory=False)
        if data_df.shape[0] < 4:
            raise ValueError(f"File {self.data_file} does not have at least 4 rows for universal format")
        headers_row = data_df.iloc[2]
        baseline_row = data_df.iloc[0]
        custom_row = data_df.iloc[1]

        gt_base: Dict[str, str] = {}
        gt_cust: Dict[str, str] = {}
        for idx, header in enumerate(headers_row):
            col_name = str(header)
            if not col_name or col_name.lower() == 'nan' or not col_name.strip():
                continue
            # Baseline
            t0 = str(baseline_row[idx]) if idx < len(baseline_row) else ''
            if t0 and t0.lower() != 'nan' and t0.strip() != '':
                if any(sfx in t0 for sfx in ('.1', '.2', '.3')):
                    t0 = t0.split('.')[0]
                gt_base[col_name] = t0
            # Custom
            t1 = str(custom_row[idx]) if idx < len(custom_row) else ''
            if t1 and t1.lower() != 'nan' and t1.strip() != '':
                if any(sfx in t1 for sfx in ('.1', '.2', '.3')):
                    t1 = t1.split('.')[0]
                gt_cust[col_name] = t1

        self._headers_row_index = 2
        self._gt_baseline = gt_base
        self._gt_custom = gt_cust
        active = gt_base if self._baseline_mode else gt_cust
        logger.info(f"Loaded universal ground truth (baseline={len(gt_base)}, custom={len(gt_cust)})")
        sample_gt = list(active.items())[:5]
        logger.debug(f"Sample ground truth mappings: {sample_gt}")
        return active

    def _load_extension_ground_truth(self) -> Dict[str, str]:
        """Legacy helper; consistent logic is in load_ground_truth."""
        data_df = pd.read_csv(self.data_file, header=None)
        semantic_types_row = data_df.iloc[0]
        column_names_row = data_df.iloc[1]
        ground_truth: Dict[str, str] = {}
        raw_headers = [str(h) for h in list(column_names_row)]
        self.unique_headers = self._build_unique_headers(raw_headers)
        for i in range(1, len(semantic_types_row)):
            if i < len(column_names_row):
                actual_column_name = self.unique_headers[i]
                st = str(semantic_types_row.iloc[i])
                if st and st != 'nan' and st.strip() != '':
                    if any(suffix in st for suffix in ('.1', '.2', '.3')):
                        ground_truth[actual_column_name] = st.split('.')[0]
                    else:
                        ground_truth[actual_column_name] = st
        return ground_truth

    def _load_uagi_ground_truth(self) -> Dict[str, str]:
        """Legacy helper; consistent logic is in load_ground_truth."""
        data_df = pd.read_csv(self.data_file, header=None)
        semantic_types_row = data_df.iloc[0]
        column_headers_row = data_df.iloc[1]
        ground_truth: Dict[str, str] = {}
        for idx, col_header in enumerate(column_headers_row):
            st = str(semantic_types_row[idx]) if idx < len(semantic_types_row) else ''
            if st and st != 'nan' and st.strip() != '':
                if any(suffix in st for suffix in ('.1', '.2', '.3')):
                    base_type = '.'.join(st.split('.')[:-1])
                    ground_truth[str(col_header)] = base_type
                else:
                    ground_truth[str(col_header)] = st
        # Also log which columns were excluded
        all_column_headers = [str(h) for h in column_headers_row]
        labeled_columns = set(ground_truth.keys())
        all_columns_set = set(all_column_headers)
        excluded = all_columns_set - labeled_columns
        if excluded:
            logger.info(f"Columns without ground truth labels: {excluded}")
        return ground_truth
    
    def ensure_aws_connected(self) -> bool:
        """DEPRECATED: AWS connection is no longer used.
        
        Custom types are now registered locally in the evaluation session
        without persisting to S3, to avoid affecting production users.

        Returns:
            Always returns True for backward compatibility.
        """
        logger.info("AWS connection skipped - using local session-based custom types")
        return True
    
    def clear_custom_types(self, preserve_existing: bool = False) -> None:
        """Clears custom semantic types from the backend.

        Args:
            preserve_existing: If True, only deletes types created during this
                evaluation session. Otherwise, all custom types are deleted.
        """
        try:
            if preserve_existing:
                if not self._created_types:
                    logger.info("No evaluation-created types to clear.")
                    return
                
                logger.debug(f"Clearing {len(self._created_types)} evaluation-created types...")
                for semantic_type in self._created_types:
                    delete_response = self.session.delete(f"{BASE_URL}/semantic-types/{semantic_type}")
                    if delete_response.status_code in [200, 204]:
                        logger.debug(f"Deleted evaluation type: {semantic_type}")
                self._created_types.clear()
            else:
                self._clear_all_custom_types()
                
        except Exception as e:
            logger.error(f"Error clearing custom types: {e}")

    def _clear_all_custom_types(self) -> None:
        """Fetches and deletes all custom semantic types from the backend."""
        logger.info("Clearing ALL custom types...")
        try:
            response = self.session.get(f"{BASE_URL}/semantic-types/custom-only")
            if response.status_code != 200:
                logger.warning(f"Failed to fetch custom types: {response.status_code}")
                return

            custom_types = response.json()
            cleared_count, skipped_count, failed_count = 0, 0, 0

            for custom_type in custom_types:
                semantic_type = custom_type.get('semanticType')
                if not semantic_type:
                    continue

                delete_response = self.session.delete(f"{BASE_URL}/semantic-types/{semantic_type}")
                if delete_response.status_code in [200, 204]:
                    cleared_count += 1
                    logger.debug(f"Deleted custom type: {semantic_type}")
                elif delete_response.status_code == 404:
                    skipped_count += 1
                    logger.debug(f"Type not found (404), skipping: {semantic_type}")
                else:
                    failed_count += 1
                    logger.warning(f"Failed to delete {semantic_type}: {delete_response.status_code}")
            
            logger.info(f"Custom type clearing summary - Deleted: {cleared_count}, Not found: {skipped_count}, Failed: {failed_count}\n")

        except Exception as e:
            logger.error(f"Error while clearing all custom types: {e}")

    def enable_builtin_types(self) -> None:
        """Re-enables built-in semantic types.
        
        Note:
            This is a placeholder for future functionality.
        """
        logger.info("Re-enabling built-in semantic types...")
        pass
    
    def load_generated_types(self, desc_num: int, timestamp: Optional[str] = None) -> List[Dict]:
        """Loads generated semantic types from a file.

        Args:
            desc_num: The description pattern number.
            timestamp: An optional timestamp to load a specific version. If None,
                the latest version is loaded.

        Returns:
            A list of valid semantic type definitions, or an empty list if
            the file is not found or contains no valid types.
        """
        filename = self._find_generated_types_file(desc_num, timestamp)
        if not filename or not filename.exists():
            logger.warning(f"Generated types file not found for desc_num {desc_num}")
            return []

        return self._load_types_from_file(filename)

    def _find_generated_types_file(self, desc_num: int, timestamp: Optional[str]) -> Optional[Path]:
        """Finds the generated types file for a given description and timestamp.

        Args:
            desc_num: The description pattern number.
            timestamp: An optional timestamp for a specific version.

        Returns:
            A Path object to the file, or None if not found.
        """
        if timestamp:
            return GENERATED_TYPES_DIR / f"{self.dataset}_description{desc_num}_{timestamp}.json"
        
        # Find timestamped versions for a candidate tag
        def find_for_tag(tag: str) -> List[str]:
            patt = str(GENERATED_TYPES_DIR / f"{tag}_description{desc_num}_*.json")
            return sorted(glob.glob(patt))
        # Try dataset and dynamic candidates in order
        search_tags: List[str] = []
        seen_tags: Set[str] = set()
        for t in [self.dataset] + getattr(self, "_dataset_candidates", []):
            if t and t not in seen_tags:
                search_tags.append(t)
                seen_tags.add(t)
        timestamped_files: List[str] = []
        used_tag: Optional[str] = None
        for tag in search_tags:
            files = find_for_tag(tag)
            if files:
                timestamped_files = files
                used_tag = tag
                if tag != self.dataset:
                    logger.info(
                        f"Using generated types found for tag '{tag}' (derived from inputs) while evaluating dataset '{self.dataset}'"
                    )
                break
        if timestamped_files:
            # Choose the most recent by timestamp suffix in filename (YYYYMMDD_HHMMSS)
            def extract_ts(name: str) -> str:
                m = re.search(r"_(\d{8}_\d{6})\.json$", name)
                return m.group(1) if m else "00000000_000000"
            selected = max(timestamped_files, key=lambda p: extract_ts(Path(p).name))
            versions = [Path(f).name for f in timestamped_files]
            if len(timestamped_files) > 1:
                logger.info(
                    "Found %d version(s) for %s description %d: %s",
                    len(timestamped_files), used_tag or self.dataset, desc_num, versions,
                )
                logger.info(
                    "Using most recent: %s. To pin a specific version, pass --timestamp YYYYMMDD_HHMMSS",
                    Path(selected).name,
                )
            else:
                logger.info(
                    "Using generated types: %s.",
                    Path(selected).name
                )
            return Path(selected)
            
        # Fallback to legacy format
        for tag in search_tags:
            legacy_file = GENERATED_TYPES_DIR / f"{tag}_description{desc_num}.json"
            if legacy_file.exists():
                if tag != self.dataset:
                    logger.info(
                        f"Using legacy generated types found for tag '{tag}' (derived from inputs) while evaluating dataset '{self.dataset}'"
                    )
                return legacy_file
        return None

    def _load_types_from_file(self, filename: Path) -> List[Dict]:
        """Loads and validates semantic types from a JSON file.

        Args:
            filename: The path to the JSON file.

        Returns:
            A list of valid semantic type definitions.
        """
        try:
            with open(filename, 'r') as f:
                data = json.load(f)
            
            # Extract generated_types from the JSON structure
            if isinstance(data, list):
                types = data
            elif isinstance(data, dict):
                types = data.get("generated_types", [])
            else:
                types = []
            
            # Filter out types with errors (resultType == "error")
            valid_types = [t for t in types if t.get("resultType") != "error"]
            if len(valid_types) < len(types):
                error_count = len(types) - len(valid_types)
                logger.warning(f"Filtered out {error_count} types with errors from {filename.name}")
            
            logger.info(f"Loaded {len(valid_types)} valid types from {filename.name}")
            return valid_types
        except (json.JSONDecodeError, IOError) as e:
            logger.error(f"Error loading or parsing types from {filename.name}: {e}")
            return []
    
    def _determine_base_type(self, generated_type: Dict) -> str:
        """Determines the base type for a generated semantic type.

        Args:
            generated_type: The generated type definition.

        Returns:
            The determined base type (e.g., 'STRING', 'LONG').
        """
        if generated_type.get("baseType"):
            base_type = generated_type["baseType"]
            return "STRING" if base_type.lower() == "string" else base_type
        
        if generated_type.get("pluginType") == "regex":
            pattern = generated_type.get("regexPattern", "")
            # Heuristic: check if the pattern is likely numeric.
            stripped = pattern.replace('^', '').replace('$', '')
            numeric_like = all(ch in "\\d{}0123456789" for ch in stripped)
            return "LONG" if numeric_like else "STRING"
        
        if generated_type.get("pluginType") == "list":
            values = [str(v) for v in (generated_type.get("listValues") or []) if v is not None]
            return "LONG" if values and all(v.isdigit() for v in values) else "STRING"
        
        return "STRING"
    
    def _determine_priority(self, generated_type: Dict) -> int:
        """Determines the priority for a generated semantic type.

        Args:
            generated_type: The generated type definition.

        Returns:
            The priority value.
        """
        generated_priority = generated_type.get("priority")
        return generated_priority if generated_priority is not None else 1000
    
    def create_custom_type(self, generated_type: Dict) -> bool:
        """Creates a custom semantic type from a generated definition.

        Args:
            generated_type: The definition of the type to create.

        Returns:
            True if the type was created successfully, False otherwise.
        """
        try:
            if not self._is_valid_generated_type(generated_type):
                return False

            custom_type_payload = self._build_custom_type_payload(generated_type)
            if not custom_type_payload:
                return False

            return self._upsert_custom_type(custom_type_payload)

        except Exception as e:
            logger.error(f"Error creating custom type: {e}")
            return False

    def _is_valid_generated_type(self, generated_type: Dict) -> bool:
        """Validates if the generated type has the required fields.

        Args:
            generated_type: The generated type definition to validate.

        Returns:
            True if the type is valid, False otherwise.
        """
        if self._only_types_regex:
            name = generated_type.get("semanticType", "")
            if not re.search(self._only_types_regex, name):
                return False

        if generated_type.get("pluginType") == "java":
            logger.warning(f"Skipping {generated_type.get('semanticType')} - 'java' plugin type not supported.")
            return False

        required_fields = ["semanticType", "description", "pluginType"]
        for field in required_fields:
            if not generated_type.get(field):
                logger.warning(f"Skipping type with missing required field: {field}")
                return False
        
        return True

    def _build_custom_type_payload(self, generated_type: Dict) -> Optional[Dict]:
        """Builds the API payload for creating a custom semantic type.

        Args:
            generated_type: The generated type definition.

        Returns:
            A dictionary representing the API payload, or None if validation fails.
        """
        plugin_type = generated_type["pluginType"]
        
        raw_threshold = generated_type.get("confidenceThreshold")
        threshold_int: Optional[int] = None
        try:
            if raw_threshold is None:
                threshold_int = 95
            elif isinstance(raw_threshold, (float, int)) and float(raw_threshold) <= 1.0:
                threshold_int = int(round(float(raw_threshold) * 100))
            else:
                threshold_int = int(raw_threshold)
        except (ValueError, TypeError):
            threshold_int = 95
        # Clamp to evaluator-acceptable range [80, 100]
        if threshold_int < 80:
            logger.debug(f"Clamping threshold for {generated_type.get('semanticType')} from {threshold_int} to 95")
            threshold_int = 95
        if threshold_int > 100:
            threshold_int = 100

        custom_type = {
            "semanticType": generated_type["semanticType"].strip(),
            "description": generated_type["description"].strip(),
            "pluginType": plugin_type,
            "baseType": self._determine_base_type(generated_type),
            "threshold": threshold_int,
            "priority": int(self._determine_priority(generated_type)),
            "validLocales": [{"localeTag": "*"}]
        }

        # Handle type-specific content
        if plugin_type == "list":
            if not self._add_list_content(custom_type, generated_type):
                return None
        elif plugin_type == "regex":
            if not self._add_regex_content(custom_type, generated_type):
                return None
        
        self._add_header_patterns(custom_type, generated_type)

        return custom_type

    def _add_list_content(self, custom_type: Dict, generated_type: Dict) -> bool:
        """Adds content for 'list' plugin types to the payload.

        Args:
            custom_type: The payload dictionary to modify.
            generated_type: The generated type definition.

        Returns:
            True if successful, False if validation fails.
        """
        list_values = generated_type.get("listValues")
        if not isinstance(list_values, list) or not list_values:
            logger.error(f"List type {generated_type['semanticType']} missing listValues.")
            return False

        seen = set()
        members = []
        for v in list_values:
            if v is None: continue
            s = str(v).strip()
            if not s: continue
            u = s.upper()
            if u in seen: continue
            seen.add(u)
            members.append(u)
        
        custom_type["content"] = {"type": "inline", "values": members}
        
        backout = (generated_type.get("backout") or "").strip()
        if not backout:
            logger.error(f"List type {generated_type['semanticType']} missing backout.")
            return False
        custom_type["backout"] = backout
        return True

    def _add_regex_content(self, custom_type: Dict, generated_type: Dict) -> bool:
        """Adds content for 'regex' plugin types to the payload.

        Args:
            custom_type: The payload dictionary to modify.
            generated_type: The generated type definition.

        Returns:
            True if successful, False if validation fails.
        """
        regex_pattern = (generated_type.get("regexPattern") or "").strip()
        if not regex_pattern:
            logger.error(f"Regex type {generated_type['semanticType']} missing regexPattern.")
            return False

        custom_type["validLocales"][0]["matchEntries"] = [{
            "regExpReturned": regex_pattern,
            "isRegExpComplete": True
        }]
        return True

    def _add_header_patterns(self, custom_type: Dict, generated_type: Dict) -> None:
        """Adds header patterns to the custom type payload.

        Args:
            custom_type: The payload dictionary to modify.
            generated_type: The generated type definition.
        """
        header_patterns = []
        for hp in (generated_type.get("headerPatterns") or []):
            try:
                reg = (hp.get("regExp") or "").strip()
                if not reg: continue
                header_patterns.append({
                    "regExp": reg,
                    "confidence": int(hp.get("confidence", 95)),
                    "mandatory": bool(hp.get("mandatory", False))
                })
            except Exception:
                continue
        
        if header_patterns:
            custom_type["validLocales"][0]["headerRegExps"] = header_patterns

    def _upsert_custom_type(self, custom_type: Dict) -> bool:
        """Creates or updates a custom type via the API.

        Args:
            custom_type: The API payload for the custom type.

        Returns:
            True if the upsert was successful, False otherwise.
        """
        if not self._existing_types_cache:
            self._refresh_existing_types_cache()

        type_name = custom_type["semanticType"]
        existing = self._existing_types_cache.get(type_name)

        if existing and self._is_same_type(existing, custom_type):
            logger.debug(f"Skipping unchanged type {type_name}")
            return False

        if self._dry_run_create:
            logger.debug(f"DRY-RUN: Would {'update' if existing else 'create'} {type_name}")
            return False
        
        # Add ephemeral flag to indicate session-only type (no S3 persistence)
        custom_type["ephemeral"] = True
        
        # Attempt initial API call
        method = "PUT" if existing else "POST"
        url = f"{BASE_URL}/semantic-types"
        if method == "PUT":
            url += f"/{type_name}"

        response = self.session.request(method, url, json=custom_type)

        # Fallback logic for upsert
        if response.status_code == 404 and method == "PUT":
            logger.debug(f"PUT failed for {type_name}, retrying with POST.")
            response = self.session.post(f"{BASE_URL}/semantic-types", json=custom_type)
        elif response.status_code == 409 and method == "POST":
            logger.debug(f"POST failed for {type_name}, retrying with PUT.")
            response = self.session.put(f"{BASE_URL}/semantic-types/{type_name}", json=custom_type)
        
        # Process final response
        if response.status_code in [200, 201]:
            self._created_types.add(type_name)
            self._existing_types_cache[type_name] = custom_type
            logger.debug(f"Successfully created type {type_name} [plugin: {custom_type['pluginType']}]")
            logger.debug(f"Request payload for {type_name}: {json.dumps(custom_type, indent=2)}")
            return True
        else:
            logger.error(f"Failed to create type {type_name}: {response.status_code}")
            logger.error(f"Response: {response.text}")
            logger.error(f"Request payload: {json.dumps(custom_type, indent=2)}")
            return False

    def _infer_backout_from_values(self, members: List[str]) -> str:
        """Infer an FTA-like backout pattern from list members by value shape.
        Examples inspired by plugins.json:
          - 2-char alpha codes → "\\p{IsAlphabetic}{2}"
          - 3-char alpha codes → "\\p{IsAlphabetic}{3}"
          - 5-digit zip → "\\d{5}"
          - Mixed words with spaces/dots → "[- \\p{IsAlphabetic}\\.]+"
          - General phrases with spaces → "[ \\p{IsAlphabetic}]+"
        """
        if not members:
            return "[ \\p{IsAlphabetic}]+"
        vals = [m for m in members if isinstance(m, str) and m]
        if not vals:
            return "[ \\p{IsAlphabetic}]+"
        # Normalize
        # All digits and same length
        if all(v.isdigit() for v in vals):
            lens = {len(v) for v in vals}
            if len(lens) == 1:
                n = list(lens)[0]
                return f"\\d{{{n}}}"
            return "\\d{2,}"
        # All alphabetic only (ASCII letters) and same length
        if all(re.fullmatch(r"[A-Z]+", v) for v in vals):
            lens = {len(v) for v in vals}
            if len(lens) == 1:
                n = list(lens)[0]
                return f"\\p{{IsAlphabetic}}{{{n}}}"
            return "\\p{IsAlphabetic}{2,}"
        # Alpha words with spaces/dots/hyphens
        if all(re.fullmatch(r"[A-Z][A-Z '\\.-]*", v) for v in vals):
            # include hyphen and dot per many built-ins
            return "[- \\p{IsAlphabetic}\\.]+"
        # Fallback conservative phrase
        return "[ \\p{IsAlphabetic}]+"

    def _refresh_existing_types_cache(self) -> None:
        """Refreshes the local cache of existing custom types from the API."""
        try:
            resp = self.session.get(f"{BASE_URL}/semantic-types/custom-only")
            if resp.status_code == 200:
                types = resp.json()
                cache = {}
                for t in types:
                    name = t.get("semanticType")
                    if name:
                        cache[name] = t
                self._existing_types_cache = cache
            else:
                logger.warning(f"Failed to fetch existing custom types: {resp.status_code}")
        except Exception as e:
            logger.warning(f"Error fetching existing types: {e}")

    def _is_same_type(self, existing: Dict, new: Dict) -> bool:
        """Compares two type definitions to see if they are identical.

        Args:
            existing: The existing type definition from the cache.
            new: The new type definition to be created.

        Returns:
            True if the types are functionally identical, False otherwise.
        """
        try:
            def pick(d, keys):
                return {k: d.get(k) for k in keys}

            keys = ["pluginType", "baseType", "threshold", "priority", "backout"]
            a = pick(existing, keys)
            b = pick(new, keys)

            def norm_locales(x):
                out = []
                for loc in (x.get("validLocales") or []):
                    out.append({
                        "localeTag": loc.get("localeTag", "*"),
                        "headerRegExps": [
                            {
                                "regExp": h.get("regExp"),
                                "confidence": int(h.get("confidence", 95)),
                                "mandatory": bool(h.get("mandatory", False))
                            } for h in (loc.get("headerRegExps") or [])
                        ],
                        "matchEntries": [
                            {
                                "regExpReturned": m.get("regExpReturned"),
                                "isRegExpComplete": bool(m.get("isRegExpComplete", True))
                            } for m in (loc.get("matchEntries") or [])
                        ]
                    })
                return out

            a_loc = norm_locales(existing)
            b_loc = norm_locales(new)

            def norm_content(x):
                c = x.get("content") or {}
                return {
                    "type": c.get("type"),
                    "values": c.get("values")
                }

            a.update({"validLocales": a_loc, "content": norm_content(existing)})
            b.update({"validLocales": b_loc, "content": norm_content(new)})

            return json.dumps(a, sort_keys=True) == json.dumps(b, sort_keys=True)
        except Exception:
            return False
    
    def profile_data(self, custom_only: bool = False) -> Dict:
        """Profiles the data using the current semantic types.

        Args:
            custom_only: If True, only custom types are used for profiling.

        Returns:
            The raw JSON response from the profiling API.
        """
        try:
            # Unified UAGI/extension loader: structure is the same across datasets
            # Row 0: Ground truth semantic types
            # Row 1: Actual column headers
            # Row 2+: Data
            data_df = pd.read_csv(self.data_file, header=None, dtype=str, low_memory=False)
            logger.info(f"Loaded CSV shape: {data_df.shape}")
            # Combined, structured mapping log for clarity
            logger.debug(
                "Using ground truth mapping (header -> type):\n%s",
                json.dumps(self.ground_truth, indent=2, ensure_ascii=False)
            )
            # Determine header row index (universal: 2; legacy: 1)
            header_idx = self._headers_row_index if self._headers_row_index is not None else 1
            actual_headers = data_df.iloc[header_idx].tolist()
            # Data is now clean; use headers as-is
            data_df = data_df.iloc[header_idx + 1:].reset_index(drop=True)
            data_df.columns = actual_headers

            # Column cap for large eval datasets to avoid massive JSON payloads (generic, not dataset-specific)
            if not self._full_data:
                max_cols_cap = _eval_get_int("EVAL_MAX_COLUMNS", eval_default=300, fallback_default=300)
                if len(data_df.columns) > max_cols_cap:
                    data_df = data_df[data_df.columns[:max_cols_cap]]
                    actual_headers = list(data_df.columns)
                    logger.info(f"EVAL_MAX_COLUMNS cap applied: columns={len(actual_headers)}")
            logger.info(f"After processing: {data_df.shape} with columns: {list(data_df.columns)}")
            
            # Optionally truncate dataset to reduce memory/heap usage and apply a hard cap (generic)
            if self._full_data:
                should_truncate_flag = False
            else:
                should_truncate_flag = _eval_get_bool("EVAL_TRUNCATE_DATA", eval_default=True, fallback_default=True)
            if should_truncate_flag:
                original_rows = len(data_df)
                row_cap = _eval_get_int("EVAL_MAX_ROWS", eval_default=1000, fallback_default=1000)
                if original_rows > row_cap:
                    data_df = data_df.iloc[:row_cap].reset_index(drop=True)
                    logger.info(
                        f"EVAL_TRUNCATE_DATA applied: original={original_rows}, final_rows={len(data_df)} (cap={row_cap})"
                    )

            # Prepare request
            columns = list(data_df.columns)
            data_rows = []

            # Include ALL rows for profiling (no sampling)
            for _, row in data_df.iterrows():
                row_dict = {}
                for col in columns:
                    value = row[col]
                    s = None if value is None else str(value)
                    if s is None or s.strip() == '' or s.lower() == 'nan':
                        row_dict[col] = None
                    else:
                        row_dict[col] = s
                data_rows.append(row_dict)
            
            request = {
                "table_name": f"{self.dataset}_table",
                "columns": columns,
                "data": data_rows,
                # Bound what the backend will train on; avoids JSON→object blowups
                "max_samples": len(data_rows),
                "include_statistics": False,
                "custom_only": custom_only
            }
            
            logger.info(f"Profiling request: {len(columns)} columns, {len(data_rows)} rows, custom_only={custom_only}")

            # Profile the data with one retry on transient connection errors
            logger.info(f"Sending profiling request to {BASE_URL}/classify/table...")
            response = self.session.post(
                f"{BASE_URL}/classify/table",
                json=request,
                timeout=300  # Increased to 5 minutes for large datasets
            )
            
            logger.debug(f"Received response with status code: {response.status_code}")
            
            if response.status_code == 200:
                logger.info("Data profiled successfully.")
                return response.json()
            else:
                raise RuntimeError(
                    f"Profiling failed with status {response.status_code}: {response.text[:500]}"
                )
                
        except requests.exceptions.Timeout as e:
            raise TimeoutError("Profiling request timed out (5 minutes).") from e
        except Exception as e:
            raise RuntimeError(f"Error profiling data: {e}") from e
    
    def calculate_metrics(self, predictions: Dict) -> Dict:
        """Calculates evaluation metrics based on profiling predictions.

        Args:
            predictions: The raw prediction output from the profiling API.

        Returns:
            A dictionary containing the calculated metric scores.
        """
        classifications = self._parse_predictions(predictions)
        results_detail, excluded_columns = self._compute_classification_details(classifications)
        
        self._log_classification_summary(results_detail, len(excluded_columns))
        
        return self._calculate_metric_scores(results_detail, len(excluded_columns))

    def _parse_predictions(self, predictions: Dict) -> Dict[str, Dict]:
        """Parses the raw prediction response into a standardized format.

        Args:
            predictions: The raw prediction output from the profiling API.

        Returns:
            A dictionary mapping column names to their predicted semantic type.
        """
        # Prefer map if available
        if 'column_classifications' in predictions and isinstance(predictions['column_classifications'], dict):
            return predictions['column_classifications']
        # Fallback to list form; normalize snake/camel keys
        cols = predictions.get('columns')
        if isinstance(cols, list):
            normalized: Dict[str, Dict] = {}
            for col in cols:
                if not isinstance(col, dict):
                    continue
                name = col.get('column_name') or col.get('columnName') or col.get('name') or ''
                semt = col.get('semantic_type') or col.get('semanticType')
                entry = dict(col)
                if semt is not None:
                    entry['semantic_type'] = semt
                if name:
                    normalized[name] = entry
            return normalized
        raise ValueError("Predictions payload missing 'column_classifications' or 'columns' structure")

    def _compute_classification_details(self, classifications: Dict, ground_truth: Optional[Dict] = None) -> tuple[List[Dict], List[str]]:
        """Compares predictions against ground truth to produce detailed results.

        Args:
            classifications: A dictionary of column classifications.

        Returns:
            A tuple containing a list of detailed result dictionaries and a
            list of excluded column names.
        """
        results_detail = []
        excluded_columns = []

        gt = ground_truth if ground_truth is not None else getattr(self, 'ground_truth', {})
        for col_name, classification in classifications.items():
            true_type = gt.get(col_name)

            if not true_type or true_type == 'nan':
                excluded_columns.append(col_name)
                logger.debug(f"Excluding column '{col_name}' from metrics: no ground truth label.")
                continue

            predicted_type = classification.get('semantic_type') or classification.get('semanticType') or 'NONE'
            is_correct = (predicted_type == true_type)

            results_detail.append({
                "column": col_name,
                "predicted": predicted_type,
                "actual": true_type,
                "correct": is_correct
            })

        return results_detail, excluded_columns

    

    def _log_classification_summary(self, results_detail: List[Dict], excluded_count: int) -> None:
        """Logs a summary of the classification results.

        Args:
            results_detail: A list of detailed result dictionaries.
            excluded_count: The number of columns excluded from evaluation.
        """
        total = len(results_detail)
        if total == 0:
            logger.info("No columns with ground truth labels were evaluated.")
            return

        classified_columns = [d for d in results_detail if d['predicted'] != 'NONE']
        unclassified_columns = [d for d in results_detail if d['predicted'] == 'NONE']
        
        logger.info("Classification Summary:")
        logger.info(f"  Successfully classified: {len(classified_columns)}/{total} columns")
        logger.info(f"  Not classified: {len(unclassified_columns)}/{total} columns")
        
        if classified_columns:
            logger.info("  Classified columns (all):")
            for detail in classified_columns:
                match_symbol = "✓" if detail['correct'] else "✗"
                logger.info(f"    {match_symbol} {detail['column']}: {detail['predicted']} (expected: {detail['actual']})")

        if unclassified_columns:
            logger.info("  Not classified columns (all):")
            for detail in unclassified_columns:
                logger.info(f"    ✗ {detail['column']}: NONE (expected: {detail['actual']})")

        if excluded_count > 0:
            logger.info(f"  Excluded {excluded_count} columns without ground truth from evaluation.")

    def _calculate_metric_scores(self, results_detail: List[Dict], excluded_count: int) -> Dict:
        """Calculates precision, recall, F1, and accuracy from detailed results.

        Args:
            results_detail: A list of detailed result dictionaries.
            excluded_count: The number of columns excluded from evaluation.

        Returns:
            A dictionary containing the calculated metric scores.
        """
        correct = sum(1 for d in results_detail if d['correct'])
        true_positives = sum(1 for d in results_detail if d['correct'] and d['predicted'] != 'NONE')
        false_positives = sum(1 for d in results_detail if not d['correct'] and d['predicted'] != 'NONE')
        false_negatives = sum(1 for d in results_detail if not d['correct'] and d['actual'] != 'NONE')
        
        total = len(results_detail)
        accuracy = correct / total if total > 0 else 0
        precision = true_positives / (true_positives + false_positives) if (true_positives + false_positives) > 0 else 0
        recall = true_positives / (true_positives + false_negatives) if (true_positives + false_negatives) > 0 else 0
        f1_score = 2 * (precision * recall) / (precision + recall) if (precision + recall) > 0 else 0
        
        return {
            "accuracy": accuracy,
            "precision": precision,
            "recall": recall,
            "f1_score": f1_score,
            "total_columns": total,
            "excluded_columns": excluded_count,
            "correct_predictions": correct,
            "true_positives": true_positives,
            "false_positives": false_positives,
            "false_negatives": false_negatives,
            "details": results_detail
        }
    
    def evaluate_description(self, desc_num: int, timestamp: Optional[str] = None) -> Dict:
        """Evaluates a single description pattern against the ground truth.

        Args:
            desc_num: The description pattern number to evaluate.
            timestamp: An optional timestamp for a specific version.

        Returns:
            A dictionary containing the evaluation metrics.
        """
        logger.info(f"Evaluating Description Pattern {desc_num}")
        logger.info("=" * 60)
        
        self.clear_custom_types()
        
        generated_types = self.load_generated_types(desc_num, timestamp)
        if not generated_types:
            raise FileNotFoundError(f"No generated types found for description {desc_num}")
        
        created_count = 0
        for gen_type in generated_types:
            if self.create_custom_type(gen_type):
                created_count += 1
        
        logger.debug(f"Created {created_count}/{len(generated_types)} custom types")
        
        # Multi-file universal flow
        if getattr(self, 'input_files', None):
            per_file: List[Dict] = []
            for f in self.input_files:
                headers, rows, _, gt_custom = self._load_universal(f)
                preds = self.profile_rows(headers, rows, custom_only=True)
                results_detail, excluded_columns = self._compute_classification_details(self._parse_predictions(preds), gt_custom)
                per_file.append(self._calculate_metric_scores(results_detail, len(excluded_columns)))
            metrics = self._aggregate_metrics_list(per_file)
        else:
            predictions = self.profile_data()
            metrics = self.calculate_metrics(predictions)
        
        logger.info(f"Description {desc_num} Results:")
        logger.info(f"    Accuracy: {metrics['accuracy']:.3f}")
        logger.info(f"    Precision: {metrics['precision']:.3f}")
        logger.info(f"    Recall: {metrics['recall']:.3f}")
        logger.info(f"    F1 Score: {metrics['f1_score']:.3f}")
        if 'excluded_columns' in metrics and metrics['excluded_columns'] > 0:
            logger.info(f"  (Excluded {metrics['excluded_columns']} columns without ground truth)")
        
        if metrics.get('details'):
            logger.debug("Sample predictions (first 5):")
            for detail in metrics['details'][:5]:
                logger.debug(f"  {detail['column']}: predicted={detail['predicted']}, actual={detail['actual']}, correct={detail['correct']}")
        
        self.clear_custom_types()
        
        return metrics
    
    def run_baseline_evaluation(self, clear_custom: bool = True) -> Dict:
        """Runs a baseline evaluation using only built-in semantic types.

        Args:
            clear_custom: If True, clears all custom types for a clean baseline.

        Returns:
            A dictionary containing the baseline evaluation results.
        """
        logger.info("=" * 60)
        logger.info("BASELINE EVALUATION - Built-in Types Only")
        logger.info("=" * 60)
        
        if clear_custom:
            logger.info("Clearing custom types for clean baseline...")
            self.clear_custom_types()
        else:
            logger.info("Keeping existing custom types (baseline will still use built-in only)")
        
        if getattr(self, 'input_files', None):
            per_file: List[Dict] = []
            for f in self.input_files:
                headers, rows, gt_base, _ = self._load_universal(f)
                preds = self.profile_rows(headers, rows, custom_only=False)
                results_detail, excluded_columns = self._compute_classification_details(self._parse_predictions(preds), gt_base)
                per_file.append(self._calculate_metric_scores(results_detail, len(excluded_columns)))
            metrics = self._aggregate_metrics_list(per_file)
        else:
            predictions = self.profile_data(custom_only=False)
            metrics = self.calculate_metrics(predictions)
        
        logger.info("Baseline Results:")
        logger.info(f"    Accuracy: {metrics['accuracy']:.3f}")
        logger.info(f"    Precision: {metrics['precision']:.3f}")
        logger.info(f"    Recall: {metrics['recall']:.3f}")
        logger.info(f"    F1 Score: {metrics['f1_score']:.3f}")
        if 'excluded_columns' in metrics and metrics['excluded_columns'] > 0:
            logger.info(f"  (Excluded {metrics['excluded_columns']} columns without ground truth)")
        
        results = {
            'dataset': self.dataset,
            'mode': 'baseline',
            'results': {
                'baseline': metrics
            },
            'timestamp': datetime.now().isoformat()
        }
        
        self.save_results(results)
        self.print_summary({'baseline': metrics})
        
        return results
    
    # Helper to compute per-column correctness used in progress-run summary
    # (placed here to ensure availability before usage)
    def _compute_correct_map(self, predictions: Dict[str, Dict], ground_truth: Dict[str, str]) -> Dict[str, bool]:
        outcome: Dict[str, bool] = {}
        for col_name, entry in predictions.items():
            true_type = ground_truth.get(col_name)
            if not true_type or true_type == 'nan':
                continue
            predicted_type = entry.get('semantic_type') or entry.get('semanticType') or 'NONE'
            outcome[col_name] = (predicted_type == true_type)
        return outcome

    def run_comparative_evaluation(self, descriptions: Optional[List[int]] = None, timestamp: Optional[str] = None) -> Dict:
        """Runs a comparative evaluation against the baseline with a single unit-based progress bar."""
        descriptions = descriptions or [1]
        print("=" * 60)
        print("🚀 COMPARATIVE EVALUATION")
        print(f"Dataset: {self.dataset}, Descriptions: {descriptions}")
        print("=" * 60)

        # Preload generated types for accurate totals
        desc_types: Dict[int, List[Dict]] = {}
        for d in descriptions:
            desc_types[d] = self.load_generated_types(d, timestamp)

        # Compute exact total units upfront
        # Baseline: len(files) + 1(metrics)
        total_units = len(self.input_files) + 1
        # Each desc: clear(1) + load(1) + create(len(types)) + profile(len(files)) + metrics(1)
        for d in descriptions:
            total_units += 1 + 1 + len(desc_types[d]) + len(self.input_files) + 1

        def _fmt_dur(s: float) -> str:
            s = int(max(0, round(s)))
            h, rem = divmod(s, 3600)
            m, sec = divmod(rem, 60)
            if h > 0:
                return f"{h:d}h {m:02d}m {sec:02d}s"
            return f"{m:d}m {sec:02d}s"

        from tqdm import tqdm
        import sys as _sys
        bar = tqdm(
            total=total_units,
            desc="Setting up",
            unit="step",
            bar_format="{desc} {percentage:3.0f}%|{bar:40}| {n_fmt}/{total_fmt} [{elapsed}<{remaining}{postfix}]",
            file=_sys.stdout,
        )
        bar.set_postfix_str("overall est: —", refresh=False)
        import time as _tm
        _start_ts = _tm.time()
        def _update_overall_est():
            done = max(1, bar.n)
            elapsed = max(0.001, _tm.time() - _start_ts)
            observed = elapsed / done
            est_total = observed * total_units
            bar.set_postfix_str(f"overall est: {_fmt_dur(est_total)}", refresh=False)

        # Mute console logging to avoid interleaving with tqdm output
        import logging as _logging
        root_logger = _logging.getLogger()
        muted_handlers = []
        for h in list(root_logger.handlers):
            if isinstance(h, _logging.StreamHandler) and not isinstance(h, _logging.FileHandler):
                root_logger.removeHandler(h)
                muted_handlers.append(h)

        # Baseline evaluation (built-in types only)
        bar.set_description("Baseline", refresh=False)
        per_file_baseline: List[Dict] = []
        baseline_correct_maps: Dict[int, Dict[str, bool]] = {}
        baseline_predictions: Dict[int, Dict[str, Dict]] = {}
        for f in self.input_files:
            headers, rows, gt_base, _ = self._load_universal(Path(f))
            preds = self.profile_rows(headers, rows, custom_only=False)
            parsed_preds = self._parse_predictions(preds)
            base_map = self._compute_correct_map(parsed_preds, gt_base)
            baseline_correct_maps[len(baseline_correct_maps)] = base_map
            baseline_predictions[len(baseline_predictions)] = parsed_preds
            results_detail, excluded = self._compute_classification_details(parsed_preds, gt_base)
            per_file_baseline.append(self._calculate_metric_scores(results_detail, len(excluded)))
            bar.update(1)
            _update_overall_est()
        baseline_metrics = self._aggregate_metrics_list(per_file_baseline)
        bar.update(1)  # metrics
        _update_overall_est()

        all_results = {
            'dataset': self.dataset,
            'baseline': baseline_metrics,
            'custom_evaluations': {},
            'timestamp': datetime.now().isoformat()
        }

        # Evaluate each description deterministically
        per_desc_custom_maps: Dict[int, Dict[int, Dict[str, bool]]] = {}
        per_desc_custom_predictions: Dict[int, Dict[int, Dict[str, Dict]]] = {}
        for desc_num in descriptions:
            # Update left label to current description
            bar.set_description(f"Description {desc_num}", refresh=False)

            # Clear
            self.clear_custom_types(preserve_existing=False)
            bar.update(1)
            _update_overall_est()

            # Load (already preloaded for totals, but do a no-op step to align units)
            _ = desc_types[desc_num]
            bar.update(1)
            _update_overall_est()

            # Create types
            created_count = 0
            for gt in desc_types[desc_num]:
                if self.create_custom_type(gt):
                    created_count += 1
                bar.update(1)
                _update_overall_est()

            # Profile per file (custom_only=True)
            per_file_custom: List[Dict] = []
            custom_maps_for_desc: Dict[int, Dict[str, bool]] = {}
            custom_predictions_for_desc: Dict[int, Dict[str, Dict]] = {}
            for f in self.input_files:
                headers, rows, _, gt_custom = self._load_universal(Path(f))
                preds = self.profile_rows(headers, rows, custom_only=True)
                parsed_preds = self._parse_predictions(preds)
                cust_map = self._compute_correct_map(parsed_preds, gt_custom)
                custom_maps_for_desc[len(custom_maps_for_desc)] = cust_map
                custom_predictions_for_desc[len(custom_predictions_for_desc)] = parsed_preds
                results_detail, excluded = self._compute_classification_details(parsed_preds, gt_custom)
                per_file_custom.append(self._calculate_metric_scores(results_detail, len(excluded)))
                bar.update(1)
                _update_overall_est()

            custom_metrics = self._aggregate_metrics_list(per_file_custom)
            bar.update(1)  # metrics
            _update_overall_est()

            delta = {
                'accuracy': custom_metrics['accuracy'] - baseline_metrics['accuracy'],
                'precision': custom_metrics['precision'] - baseline_metrics['precision'],
                'recall': custom_metrics['recall'] - baseline_metrics['recall'],
                'f1_score': custom_metrics['f1_score'] - baseline_metrics['f1_score']
            }
            all_results['custom_evaluations'][f'description_{desc_num}'] = {
                'metrics': custom_metrics,
                'delta': delta
            }
            per_desc_custom_maps[desc_num] = custom_maps_for_desc
            per_desc_custom_predictions[desc_num] = custom_predictions_for_desc

        bar.close()
        # Restore console logging handlers
        for h in muted_handlers:
            root_logger.addHandler(h)

        self._log_final_summary(all_results)
        # Final per-column outcome tables for each description
        for desc_num in descriptions:
            self._print_per_column_outcome_summary(
                desc_num,
                baseline_predictions,
                per_desc_custom_predictions.get(desc_num, {}),
                baseline_correct_maps,
                per_desc_custom_maps.get(desc_num, {})
            )
        self.save_results(all_results)
        return all_results



    def _print_per_column_outcome_summary(
        self,
        desc_num: int,
        baseline_predictions: Dict[int, Dict[str, Dict]],
        custom_predictions: Dict[int, Dict[str, Dict]],
        baseline_correct_maps: Dict[int, Dict[str, Dict[str, Any]]],
        custom_correct_maps_for_desc: Dict[int, Dict[str, Dict[str, Any]]],
    ) -> None:
        import re as _re

        def _strip_ansi(s: str) -> str:
            return _re.compile(r"\x1B(?:[@-Z\\-_]|\[[0-?]*[ -/]*[@-~])").sub("", s)

        def _pad_visible(s: str, width: int, align: str = "left") -> str:
            vis = len(_strip_ansi(s))
            pad = max(0, width - vis)
            if align == "left":
                return s + (" " * pad)
            if align == "right":
                return (" " * pad) + s
            left = pad // 2
            right = pad - left
            return (" " * left) + s + (" " * right)

        # Aggregate all columns from both baseline and custom predictions
        all_cols: Set[str] = set()
        for preds in baseline_predictions.values():
            all_cols.update(preds.keys())
        for preds in custom_predictions.values():
            all_cols.update(preds.keys())

        if not all_cols:
            print(f"\n📄 Per-column outcome (Description {desc_num})")
            print("(No labeled columns found for per-column outcome.)")
            return

        # Collect semantic type information for each column
        rows: List[Tuple[str, str, str, str]] = []
        for col in sorted(all_cols):
            # Get baseline semantic type
            baseline_type = "null"
            for preds in baseline_predictions.values():
                if col in preds and preds[col].get('semantic_type'):
                    baseline_type = preds[col]['semantic_type']
                    break

            # Get custom semantic type
            custom_type = "null"
            for preds in custom_predictions.values():
                if col in preds and preds[col].get('semantic_type'):
                    custom_type = preds[col]['semantic_type']
                    break

            # Determine outcome based on correctness maps
            base_any = any(m.get(col, False) for m in baseline_correct_maps.values())
            cust_any = any(m.get(col, False) for m in custom_correct_maps_for_desc.values())

            blue = "\033[94m"
            orange = "\033[93m"
            reset = "\033[0m"

            if base_any and cust_any:
                outcome = f"{orange}both{reset}"
            elif cust_any and not base_any:
                outcome = f"{blue}custom only{reset}"
            elif base_any and not cust_any:
                outcome = f"{orange}baseline only{reset}"
            else:
                outcome = "neither"

            rows.append((col, baseline_type, custom_type, outcome))

        # Compute widths based on visible text
        max_col_w = max(len("Column"), max(len(col) for col, _, _, _ in rows))
        max_base_w = max(len("Baseline Classification"), max(len(base) for _, base, _, _ in rows))
        max_custom_w = max(len("Custom Classification"), max(len(custom) for _, _, custom, _ in rows))
        max_out_w = max(len("Outcome"), max(len(_strip_ansi(outcome)) for _, _, _, outcome in rows))

        # Print table
        print(f"\n📄 Per-column outcome (Description {desc_num})")
        print("┌" + "─" * (max_col_w + 2) + "┬" + "─" * (max_base_w + 2) + "┬" + "─" * (max_custom_w + 2) + "┬" + "─" * (max_out_w + 2) + "┐")
        print(f"│ {_pad_visible('Column', max_col_w)} │ {_pad_visible('Baseline Classification', max_base_w)} │ {_pad_visible('Custom Classification', max_custom_w)} │ {_pad_visible('Outcome', max_out_w)} │")
        print("├" + "─" * (max_col_w + 2) + "┼" + "─" * (max_base_w + 2) + "┼" + "─" * (max_custom_w + 2) + "┼" + "─" * (max_out_w + 2) + "┤")

        for col, base_type, custom_type, outcome in rows:
            print(f"│ {_pad_visible(col, max_col_w)} │ {_pad_visible(base_type, max_base_w)} │ {_pad_visible(custom_type, max_custom_w)} │ {_pad_visible(outcome, max_out_w)} │")

        print("└" + "─" * (max_col_w + 2) + "┴" + "─" * (max_base_w + 2) + "┴" + "─" * (max_custom_w + 2) + "┴" + "─" * (max_out_w + 2) + "┘")

    def _run_and_log_baseline(self) -> Dict:
        """Runs and logs the baseline evaluation.

        Returns:
            A dictionary containing the baseline metrics.
        """
        print("📊 Running Baseline Evaluation (Built-in Types)")
        print("-" * 50)
        # Enable baseline mode to use row 0 GT from universal data files
        self._baseline_mode = True
        try:
            self.ground_truth = self.load_ground_truth()
        except Exception:
            pass

        # Baseline runs with built-ins only (custom_only=False)
        if getattr(self, 'input_files', None):
            per_file: List[Dict] = []
            if self._parallel:
                with ThreadPoolExecutor(max_workers=self._max_workers) as ex:
                    futures = {ex.submit(self._profile_and_score, f, False): f for f in self.input_files}
                    with tqdm(total=len(futures), desc="Baseline", unit="file") as pbar:
                        for fut in as_completed(futures):
                            try:
                                per_file.append(fut.result())
                            except Exception as e:
                                logger.warning(f"Baseline profiling failed for {futures[fut]}: {e}")
                            finally:
                                pbar.update(1)
            else:
                for f in tqdm(self.input_files, desc="Baseline", unit="file"):
                    per_file.append(self._profile_and_score(f, False))
            baseline_metrics = self._aggregate_metrics_list(per_file)
        else:
            baseline_predictions = self.profile_data(custom_only=False)
            baseline_metrics = self.calculate_metrics(baseline_predictions)
        
        print("✓ Baseline Results:")
        print(f"  • Accuracy:  {baseline_metrics['accuracy']:.3f}")
        print(f"  • Precision: {baseline_metrics['precision']:.3f}")
        print(f"  • Recall:    {baseline_metrics['recall']:.3f}")
        print(f"  • F1 Score:  {baseline_metrics['f1_score']:.3f}")
        # Restore to custom mode (row 1 GT) after baseline phase
        try:
            self._baseline_mode = False
            self.ground_truth = self.load_ground_truth()
        except Exception:
            pass

        return baseline_metrics

    def _evaluate_and_compare_description(self, desc_num: int, timestamp: Optional[str], baseline_metrics: Dict, all_results: Dict) -> None:
        """Evaluates a single description and compares it to the baseline.

        Args:
            desc_num: The description pattern number.
            timestamp: An optional timestamp for a specific version.
            baseline_metrics: The baseline metrics to compare against.
            all_results: The main results dictionary to update.
        """
        print(f"🔧 Evaluating Description Pattern {desc_num}")
        print("-" * 50)

        # Strict isolation: remove evaluation-created types after each description
        self.clear_custom_types(preserve_existing=False)
        
        generated_types = self.load_generated_types(desc_num, timestamp)
        if not generated_types:
            print(f"❌ No types found for description {desc_num}")
            return

        created_count = sum(1 for gt in generated_types if self.create_custom_type(gt))
        print(f"✓ Created {created_count}/{len(generated_types)} custom types")

        self.session.post(f"{BASE_URL}/semantic-types/reload")

        # Multi-file custom metrics (compare to row1/custom GT)
        if getattr(self, 'input_files', None):
            per_file: List[Dict] = []
            if self._parallel:
                with ThreadPoolExecutor(max_workers=self._max_workers) as ex:
                    futures = {ex.submit(self._profile_and_score, f, True): f for f in self.input_files}
                    with tqdm(total=len(futures), desc="Custom", unit="file") as pbar:
                        for fut in as_completed(futures):
                            try:
                                per_file.append(fut.result())
                            except Exception as e:
                                logger.warning(f"Custom profiling failed for {futures[fut]}: {e}")
                            finally:
                                pbar.update(1)
            else:
                for f in tqdm(self.input_files, desc="Custom", unit="file"):
                    per_file.append(self._profile_and_score(f, True))
            custom_metrics = self._aggregate_metrics_list(per_file)
        else:
            custom_predictions = self.profile_data(custom_only=True)
            custom_metrics = self.calculate_metrics(custom_predictions)

        delta = {
            'accuracy': custom_metrics['accuracy'] - baseline_metrics['accuracy'],
            'precision': custom_metrics['precision'] - baseline_metrics['precision'],
            'recall': custom_metrics['recall'] - baseline_metrics['recall'],
            'f1_score': custom_metrics['f1_score'] - baseline_metrics['f1_score']
        }

        all_results['custom_evaluations'][f'description_{desc_num}'] = {
            'metrics': custom_metrics,
            'delta': delta
        }

        self._log_comparison_table(desc_num, baseline_metrics, custom_metrics, delta)
        # Per-column outcome visualization (aggregate across all input files)
        if getattr(self, 'input_files', None):
            try:
                aggregated, semantic_types = self._aggregate_per_column_outcome()
                self._print_aggregate_per_column_outcome(aggregated, semantic_types)
                # Persist full aggregate to results for this description
                all_results['custom_evaluations'][f'description_{desc_num}']['per_column_outcome'] = [
                    {
                        'column': col,
                        'total': stats['total'],
                        'baseline_correct': stats['baseline_correct'],
                        'custom_correct': stats['custom_correct'],
                        'baseline_accuracy': (stats['baseline_correct'] / stats['total']) if stats['total'] > 0 else 0.0,
                        'custom_accuracy': (stats['custom_correct'] / stats['total']) if stats['total'] > 0 else 0.0,
                    }
                    for col, stats in sorted(aggregated.items(), key=lambda kv: (-kv[1]['total'], kv[0]))
                ]
            except Exception as e:
                logger.warning(f"Failed to print aggregate per-column outcome: {e}")
        self.clear_custom_types(preserve_existing=True)

    def _log_comparison_table(self, desc_num: int, base: Dict, cust: Dict, delta: Dict) -> None:
        """Logs a formatted table comparing baseline and custom metrics with aligned columns."""
        print(f"📊 Description {desc_num} Results Comparison")
        metrics_display = [
            ("Accuracy", base['accuracy'], cust['accuracy'], delta['accuracy']),
            ("Precision", base['precision'], cust['precision'], delta['precision']),
            ("Recall", base['recall'], cust['recall'], delta['recall']),
            ("F1 Score", base['f1_score'], cust['f1_score'], delta['f1_score'])
        ]
        metric_w = max(len("Metric"), max(len(m[0]) for m in metrics_display))
        base_vals = [f"{m[1]:.3f}" for m in metrics_display]
        cust_vals = [f"{m[2]:.3f}" for m in metrics_display]
        delta_vals = [f"{m[3]:+.3f}" for m in metrics_display]
        base_w = max(len("Baseline"), max(len(s) for s in base_vals))
        cust_w = max(len("Custom"), max(len(s) for s in cust_vals))
        delta_w = max(len("Delta"), max(len(s) for s in delta_vals))
        top = "┌" + "─" * (metric_w + 2) + "┬" + "─" * (base_w + 2) + "┬" + "─" * (cust_w + 2) + "┬" + "─" * (delta_w + 2) + "┐"
        mid = "├" + "─" * (metric_w + 2) + "┼" + "─" * (base_w + 2) + "┼" + "─" * (cust_w + 2) + "┼" + "─" * (delta_w + 2) + "┤"
        bot = "└" + "─" * (metric_w + 2) + "┴" + "─" * (base_w + 2) + "┴" + "─" * (cust_w + 2) + "┴" + "─" * (delta_w + 2) + "┘"
        print(top)
        print(f"│ {'Metric'.ljust(metric_w)} │ {'Baseline'.rjust(base_w)} │ {'Custom'.rjust(cust_w)} │ {'Delta'.rjust(delta_w)} │")
        print(mid)
        for (name, b, c, d), bs, cs, ds in zip(metrics_display, base_vals, cust_vals, delta_vals):
            print(f"│ {name.ljust(metric_w)} │ {bs.rjust(base_w)} │ {cs.rjust(cust_w)} │ {ds.rjust(delta_w)} │")
        print(bot)

    def _print_per_column_outcome(self, file_path: Path, baseline_ok: Dict[str, bool], custom_ok: Dict[str, bool]) -> None:
        """Print a color-coded per-column outcome comparing baseline vs custom.

        Color legend:
          - \033[32mGREEN\033[0m: only custom got it (improvement)
          - \033[31mRED\033[0m:   only baseline got it (regression)
          - \033[34mBLUE\033[0m:  both got it
          - \033[97mWHITE\033[0m: neither got it
        """
        # GitHub Actions color alignment: use the same color for 'both', 'neither', and 'baseline only'
        BLUE = "\033[93m"   # orange/yellow
        GREEN = "\033[92m"  # bright green (positive)
        RED = "\033[93m"    # orange/yellow (baseline only)
        WHITE = "\033[93m"  # orange/yellow (neither)
        RESET = "\033[0m"

        print("")
        print(f"📄 Per-column outcome (file: {file_path})")
        print("┌──────────────────────────────────────────────────────────────┐")
        print("│ Column                               │ Base │ Cust │ Status │")
        print("├───────────────────────────────────────┼──────┼──────┼────────┤")

        # Only include columns that have custom ground truth (skip columns like No., Info, Time with no custom GT)
        all_cols = sorted(custom_ok.keys())
        for col in all_cols:
            b = baseline_ok.get(col, False)
            c = custom_ok.get(col, False)
            if c and not b:
                color = GREEN
                label = "custom only"
            elif b and not c:
                color = RED
                label = "baseline only"
            elif b and c:
                color = BLUE
                label = "both"
            else:
                color = WHITE
                label = "neither"
            base_flag = "✔" if b else "✖"
            cust_flag = "✔" if c else "✖"
            print(f"│ {col:<37} │  {base_flag}   │  {cust_flag}   │ {color}{label:<13}{RESET} │")
        print("└───────────────────────────────────────┴──────┴──────┴────────┘")

    def _aggregate_per_column_outcome(self) -> Tuple[Dict[str, Dict[str, int]], Dict[str, Dict[str, str]]]:
        """Aggregate per-column correctness across all input files.

        Returns a mapping: column -> {
            'total': labeled occurrences seen,
            'baseline_correct': count,
            'custom_correct': count,
        }
        """
        totals: Dict[str, Dict[str, int]] = {}
        semantic_types: Dict[str, Dict[str, str]] = {}  # column -> {'baseline': type, 'custom': type}
        for f in self.input_files:
            base_map = self._per_column_correct_map(f, custom_only=False)
            cust_map = self._per_column_correct_map(f, custom_only=True)
            cols = set(base_map.keys()).union(cust_map.keys())
            for col in cols:
                stats = totals.setdefault(col, {'total': 0, 'baseline_correct': 0, 'custom_correct': 0})
                stats['total'] += 1
                if base_map.get(col, {}).get('correct', False):
                    stats['baseline_correct'] += 1
                if cust_map.get(col, {}).get('correct', False):
                    stats['custom_correct'] += 1

                # Track semantic types (use the first file's types as representative)
                if col not in semantic_types:
                    semantic_types[col] = {}
                if col in base_map and 'semantic_type' in base_map[col]:
                    semantic_types[col]['baseline'] = base_map[col]['semantic_type']
                if col in cust_map and 'semantic_type' in cust_map[col]:
                    semantic_types[col]['custom'] = cust_map[col]['semantic_type']

        return totals, semantic_types

    def _print_aggregate_per_column_outcome(self, aggregated: Dict[str, Dict[str, int]], semantic_types: Dict[str, Dict[str, str]]) -> None:
        """Print an aggregate per-column outcome across all input files with aligned columns."""
        if not aggregated:
            print("\n(No per-column outcome available: no labeled columns found.)")
            return
        # Determine label per column without ANSI colors for strict alignment
        def outcome_label(stats: Dict[str, int]) -> str:
            total = stats['total']
            b_ok = stats['baseline_correct'] == total
            c_ok = stats['custom_correct'] == total
            if b_ok and c_ok:
                return "both"
            if c_ok and not b_ok:
                return "custom only"
            if b_ok and not c_ok:
                return "baseline only"
            return "neither"
        rows = []
        for col, stats in sorted(aggregated.items(), key=lambda kv: (-kv[1]['total'], kv[0])):
            baseline_type = semantic_types.get(col, {}).get('baseline', 'N/A')
            custom_type = semantic_types.get(col, {}).get('custom', 'N/A')
            rows.append((col, stats['total'], outcome_label(stats), baseline_type, custom_type))

        col_w = max(len('Column'), max(len(col) for col, _, _, _, _ in rows))
        tot_w = max(len('Total'), max(len(str(t)) for _, t, _, _, _ in rows))
        out_w = max(len('Outcome'), max(len(lbl) for _, _, lbl, _, _ in rows))
        base_type_w = max(len('Baseline Type'), max(len(bt) for _, _, _, bt, _ in rows))
        cust_type_w = max(len('Custom Type'), max(len(ct) for _, _, _, _, ct in rows))

        # Borders
        top = "┌" + "─" * (col_w + 2) + "┬" + "─" * (tot_w + 2) + "┬" + "─" * (out_w + 2) + "┬" + "─" * (base_type_w + 2) + "┬" + "─" * (cust_type_w + 2) + "┐"
        mid = "├" + "─" * (col_w + 2) + "┼" + "─" * (tot_w + 2) + "┼" + "─" * (out_w + 2) + "┼" + "─" * (base_type_w + 2) + "┼" + "─" * (cust_type_w + 2) + "┤"
        bot = "└" + "─" * (col_w + 2) + "┴" + "─" * (tot_w + 2) + "┴" + "─" * (out_w + 2) + "┴" + "─" * (base_type_w + 2) + "┴" + "─" * (cust_type_w + 2) + "┘"

        print("\n📄 Per-column outcome (aggregate across files)")
        print(top)
        print(f"│ {'Column'.ljust(col_w)} │ {'Total'.rjust(tot_w)} │ {'Outcome'.ljust(out_w)} │ {'Baseline Type'.ljust(base_type_w)} │ {'Custom Type'.ljust(cust_type_w)} │")
        print(mid)
        for col, total, label, baseline_type, custom_type in rows:
            print(f"│ {col.ljust(col_w)} │ {str(total).rjust(tot_w)} │ {label.ljust(out_w)} │ {baseline_type.ljust(base_type_w)} │ {custom_type.ljust(cust_type_w)} │")
        print(bot)

    def _log_final_summary(self, all_results: Dict) -> None:
        """Logs the final summary, highlighting the best configuration.

        Args:
            all_results: The dictionary containing all evaluation results.
        """
        print("="*60)
        print("📊 EVALUATION SUMMARY")
        print("="*60 + "\n")
        
        best_desc, best_f1 = None, -1
        for desc_key, desc_data in all_results['custom_evaluations'].items():
            f1 = desc_data['metrics']['f1_score']
            if f1 > best_f1:
                best_f1 = f1
                best_desc = desc_key
        
        if best_desc:
            best_data = all_results['custom_evaluations'][best_desc]
            print(f"🏆 Best Configuration: {best_desc}")
            print(f"  • F1 Score: {best_f1:.3f} ({best_data['delta']['f1_score']:+.3f} vs baseline)")
            print(f"  • Accuracy: {best_data['metrics']['accuracy']:.3f} ({best_data['delta']['accuracy']:+.3f} vs baseline)")
        
        print("="*60)

    def run_custom_only_evaluation(self, descriptions: Optional[List[int]] = None, timestamp: Optional[str] = None, preserve_existing: bool = True) -> Dict:
        """Runs evaluation with custom types only (no built-ins).

        Args:
            descriptions: A list of description pattern numbers to evaluate.
            timestamp: An optional timestamp for a specific version.
            preserve_existing: If True, preserves existing custom types.

        Returns:
            A dictionary containing the evaluation results.
        """
        descriptions = descriptions or [1]
        logger.info(f"Starting CUSTOM-ONLY evaluation for dataset: {self.dataset}")
        logger.info(f"Descriptions: {descriptions}, Timestamp: {timestamp}, Preserve Existing: {preserve_existing}")

        # AWS connection removed - custom types are now session-based
        logger.info("Using local session-based custom types (no S3 persistence)")

        all_results = {}
        for desc_num in descriptions:
            metrics = self._evaluate_single_description_custom_only(desc_num, timestamp)
            if metrics:
                all_results[f"description_{desc_num}_custom_only"] = metrics
        
        results_wrapper = {
            'dataset': self.dataset,
            'mode': 'custom_only',
            'descriptions': descriptions,
            'results': all_results,
            'timestamp': datetime.now().isoformat()
        }
        
        self.save_results(results_wrapper)
        self.print_summary(all_results)
        return results_wrapper

    def _evaluate_single_description_custom_only(self, desc_num: int, timestamp: Optional[str]) -> Optional[Dict]:
        """Evaluates a single description with custom types only.

        Args:
            desc_num: The description pattern number.
            timestamp: An optional timestamp for a specific version.

        Returns:
            A dictionary of metrics, or None if evaluation fails.
        """
        logger.info(f"=" * 60)
        logger.info(f"CUSTOM-ONLY EVALUATION - Description Pattern {desc_num}")
        logger.info("=" * 60)

        # Strict isolation: clear ALL existing custom types, then ONLY upsert the generated file
        logger.info("Clearing ALL custom types for a clean slate.")
        self.clear_custom_types(preserve_existing=False)

        generated_types = self.load_generated_types(desc_num, timestamp)
        if not generated_types:
            raise FileNotFoundError(f"No generated types found for description {desc_num}")

        created_count = 0
        with tqdm(total=len(generated_types), desc=f"Creating types (desc {desc_num})", unit="type") as pbar:
            for gen_type in generated_types:
                if self.create_custom_type(gen_type):
                    created_count += 1
                pbar.update(1)

        logger.info(f"Created {created_count}/{len(generated_types)} custom types")

        logger.info("Reloading semantic types...")
        try:
            reload_resp = self.session.post(f"{BASE_URL}/semantic-types/reload")
            if reload_resp.status_code == 200:
                logger.info("Semantic types reloaded successfully.")
            else:
                logger.warning(f"Semantic types reload returned status: {reload_resp.status_code}")
        except Exception as e:
            logger.warning(f"Failed to reload semantic types: {e}")

        logger.info("Running evaluation with CUSTOM TYPES ONLY.")
        if getattr(self, 'input_files', None):
            per_file: List[Dict] = []
            if self._parallel:
                with ThreadPoolExecutor(max_workers=self._max_workers) as ex:
                    futures = {ex.submit(self._profile_and_score, f, False): f for f in self.input_files}
                    with tqdm(total=len(futures), desc="Custom", unit="file") as pbar:
                        for fut in as_completed(futures):
                            try:
                                per_file.append(fut.result())
                            except Exception as e:
                                logger.warning(f"Custom profiling failed for {futures[fut]}: {e}")
                            finally:
                                pbar.update(1)
            else:
                for f in tqdm(self.input_files, desc="Custom", unit="file"):
                    per_file.append(self._profile_and_score(f, False))
            metrics = self._aggregate_metrics_list(per_file)
        else:
            predictions = self.profile_data(custom_only=True)
            metrics = self.calculate_metrics(predictions)
        
        logger.info(f"Custom-Only Description {desc_num} Results:")
        logger.info(f"  Accuracy: {metrics['accuracy']:.3f}")
        logger.info(f"  Precision: {metrics['precision']:.3f}")
        logger.info(f"  Recall: {metrics['recall']:.3f}")
        logger.info(f"  F1 Score: {metrics['f1_score']:.3f}")

        logger.info("Clearing all custom types post-evaluation.")
        self.clear_custom_types(preserve_existing=False)
        return metrics

    def run_full_evaluation(self, descriptions: Optional[List[int]] = None, timestamp: Optional[str] = None) -> None:
        """Runs a full evaluation for all specified description patterns.

        Args:
            descriptions: A list of description pattern numbers to evaluate.
            timestamp: An optional timestamp for a specific version.
        """
        if descriptions is None:
            descriptions = [1, 2, 3, 4, 5]
        
        logger.info(f"Starting profile evaluation for dataset: {self.dataset}")
        logger.info(f"Description patterns to evaluate: {descriptions}")
        if timestamp:
            logger.info(f"Using specific version: {timestamp}")
        
        # AWS connection removed - custom types are now session-based
        logger.info("Using local session-based custom types (no S3 persistence)")
        
        all_results = {}
        
        for desc_num in descriptions:
            metrics = self.evaluate_description(desc_num, timestamp)
            if metrics:
                all_results[f"description_{desc_num}"] = metrics
        
        self.save_results(all_results)
        
        self.print_summary(all_results)
        
        logger.info("Evaluation complete!")
        logger.info(f"Results saved under: {RUN_DIR}")
        logger.info("=" * 60)

    def run_mixed_evaluation(self, descriptions: Optional[List[int]] = None, timestamp: Optional[str] = None, preserve_existing: bool = True) -> Dict:
        """Runs a mixed evaluation with built-ins and generated types.

        Args:
            descriptions: A list of description pattern numbers to evaluate.
            timestamp: An optional timestamp for a specific version.
            preserve_existing: If True, preserves existing custom types.

        Returns:
            A dictionary containing the evaluation results.
        """
        descriptions = descriptions or [1]
        logger.info(f"Starting MIXED evaluation for descriptions: {descriptions}")

        # AWS connection removed - custom types are now session-based
        logger.info("Using local session-based custom types (no S3 persistence)")

        results: Dict[str, Dict] = {}
        for desc_num in descriptions:
            metrics = self._evaluate_single_description_mixed(desc_num, timestamp, preserve_existing)
            if metrics:
                results[f"description_{desc_num}_mixed"] = metrics

        wrapper = {
            'dataset': self.dataset,
            'mode': 'mixed',
            'descriptions': descriptions,
            'results': results,
            'timestamp': datetime.now().isoformat()
        }
        self.save_results(wrapper)
        self.print_summary(results)
        return wrapper

    def _evaluate_single_description_mixed(self, desc_num: int, timestamp: Optional[str], preserve_existing: bool) -> Optional[Dict]:
        """Evaluates a single description with both built-in and custom types.

        Args:
            desc_num: The description pattern number.
            timestamp: An optional timestamp for a specific version.
            preserve_existing: If True, preserves existing custom types.

        Returns:
            A dictionary of metrics, or None if evaluation fails.
        """
        logger.info("=" * 60)
        logger.info(f"MIXED EVALUATION - Description Pattern {desc_num}")
        logger.info("=" * 60)

        generated_types = self.load_generated_types(desc_num, timestamp)
        if not generated_types:
            raise FileNotFoundError(f"No generated types found for description {desc_num}")

        created_count = sum(1 for gt in generated_types if self.create_custom_type(gt))
        logger.info(f"Created {created_count}/{len(generated_types)} custom types")

        logger.info("Reloading semantic types...")
        try:
            reload_resp = self.session.post(f"{BASE_URL}/semantic-types/reload")
            logger.info(f"Reload status: {reload_resp.status_code}")
        except Exception as e:
            logger.warning(f"Failed to reload semantic types: {e}")

        logger.info("Running profiling with built-ins and custom types enabled.")
        if getattr(self, 'input_files', None):
            per_file: List[Dict] = []
            if self._parallel:
                with ThreadPoolExecutor(max_workers=self._max_workers) as ex:
                    futures = {ex.submit(self._profile_and_score, f, False): f for f in self.input_files}
                    with tqdm(total=len(futures), desc="Mixed", unit="file") as pbar:
                        for fut in as_completed(futures):
                            try:
                                per_file.append(fut.result())
                            except Exception as e:
                                logger.warning(f"Mixed profiling failed for {futures[fut]}: {e}")
                            finally:
                                pbar.update(1)
            else:
                for f in tqdm(self.input_files, desc="Mixed", unit="file"):
                    per_file.append(self._profile_and_score(f, False))
            metrics = self._aggregate_metrics_list(per_file)
        else:
            predictions = self.profile_data(custom_only=False)
            metrics = self.calculate_metrics(predictions)

        logger.info("Clearing evaluation-created types.")
        self.clear_custom_types(preserve_existing=preserve_existing)
        return metrics
    
    def save_results(self, results: Dict) -> None:
        """Saves evaluation results to a JSON file.

        Args:
            results: The dictionary containing the evaluation results.
        """
        # Write results into this run's directory (logs/<timestamp>)
        RUN_DIR.mkdir(parents=True, exist_ok=True)
        results_file = RUN_DIR / f"{self.dataset}_profile_results_{RUN_TIMESTAMP}.json"
        
        summary = {
            "dataset": self.dataset,
            "timestamp": datetime.now().isoformat(),
            "results": results
        }
        
        try:
            with open(results_file, 'w') as f:
                json.dump(summary, f, indent=2)
            logger.info(f"Saved results to {results_file}")
        except Exception as e:
            logger.error(f"Error saving results: {e}")
    
    def print_summary(self, results: Dict) -> None:
        """Prints a summary of the evaluation results.

        Args:
            results: The dictionary containing the evaluation results.
        """
        logger.info("=" * 60)
        logger.info("EVALUATION SUMMARY")
        logger.info("=" * 60 + "\n")
        
        for desc_key, metrics in results.items():
            if desc_key == 'baseline':
                logger.info(f"Baseline (Built-in Types Only):")
            elif 'custom_only' in desc_key:
                desc_num = desc_key.split('_')[1]
                logger.info(f"Custom-Only Description Pattern {desc_num}:")
            else:
                desc_num = desc_key.split('_')[1]
                logger.info(f"Description Pattern {desc_num}:")
            logger.info(f"  Accuracy:  {metrics['accuracy']:.3f}")
            logger.info(f"  Precision: {metrics['precision']:.3f}")
            logger.info(f"  Recall:    {metrics['recall']:.3f}")
            logger.info(f"  F1 Score:  {metrics['f1_score']:.3f} \n")
        
        # Find best performing description (skip for baseline-only runs)
        if results and 'baseline' not in results:
            best_desc = max(results.items(), key=lambda x: x[1]['f1_score'])
            logger.info(f"Best performing: {best_desc[0]} (F1: {best_desc[1]['f1_score']:.3f})")
        
        logger.info("=" * 60)
        # Console-only logging: no file log path
        logger.info("=" * 60)


def main() -> None:
    """Main entry point for the script."""
    parser = argparse.ArgumentParser(
        description="Profile and Evaluate Generated Semantic Types"
    )
    parser.add_argument(
        "--dataset",
        type=str,
        default="extension",
        help="Dataset to use (default: extension)"
    )
    parser.add_argument(
        "--api-base-url",
        type=str,
        default=os.environ.get("EVALUATOR_API_BASE_URL", DEFAULT_BASE_URL),
        help="API base URL (env EVALUATOR_API_BASE_URL overrides; default: http://localhost:8081/api)"
    )
    parser.add_argument(
        "--descriptions",
        type=int,
        nargs="+",
        default=[1, 2, 3, 4, 5, 6],
        help="Description patterns to evaluate (default: 1 2 3 4 5 6)"
    )
    parser.add_argument(
        "--baseline",
        action="store_true",
        help="Run baseline evaluation with built-in types only"
    )
    parser.add_argument(
        "--custom-only",
        action="store_true",
        help="Run evaluation with custom types only (no built-in types)"
    )
    parser.add_argument(
        "--comparative",
        action="store_true",
        help="Run comparative evaluation (baseline vs custom with visualizations)"
    )
    parser.add_argument(
        "--full-data",
        action="store_true",
        help="Disable truncation safeguards; send full dataset"
    )
    parser.add_argument(
        "--files",
        type=str,
        help="Comma-separated list of files and/or directories to include"
    )
    parser.add_argument(
        "--parallel",
        action="store_true",
        default=True,
        help="Enable parallel per-file profiling (default: on)"
    )
    parser.add_argument(
        "--max-workers",
        type=int,
        default=None,
        help="Max worker threads when --parallel is set (default: ThreadPool default)"
    )
    parser.add_argument(
        "--data-dir",
        type=str,
        help="Base directory for resolving relative file names; also used alone to scan all CSVs"
    )
    parser.add_argument(
        "--timestamp",
        type=str,
        default=None,
        help=(
            "Specific timestamp version to use for generated types (YYYYMMDD_HHMMSS). "
            "If multiple timestamped files exist for any selected description, this is required."
        )
    )
    parser.add_argument(
        "--log-level",
        type=str,
        default="INFO",
        choices=["DEBUG", "INFO", "WARNING", "ERROR"],
        help="Set the logging level (default: INFO)"
    )
    
    args = parser.parse_args()
    
    log_level = getattr(logging, args.log_level.upper(), logging.INFO)
    logging.getLogger().setLevel(log_level)
    
    # Console-only logging: no file log path
    logger.info("="*60)

    raw_files: List[str] = []
    if args.files:
        # split on comma and strip spaces
        raw_files = [s.strip() for s in args.files.split(',') if s.strip()]
    evaluator = ProfileEvaluator(
        dataset=args.dataset,
        base_url=args.api_base_url,
        full_data=args.full_data,
        files=raw_files,
        data_dir=args.data_dir,
        parallel=args.parallel,
        max_workers=args.max_workers,
    )
    
    # Run evaluation
    if args.baseline:
        # For baseline, we don't load any custom types, just evaluate with built-in types
        logger.info("Running BASELINE evaluation with built-in types only")
        evaluator.run_baseline_evaluation()
    elif args.custom_only:
        # For custom-only, disable built-in types and load only custom types
        logger.info("Running CUSTOM-ONLY evaluation with custom types only")
        evaluator.run_custom_only_evaluation(descriptions=args.descriptions, timestamp=args.timestamp)
    elif args.comparative:
        # Run comparative evaluation with baseline and custom types
        logger.info("Running COMPARATIVE evaluation with visualizations")
        evaluator.run_comparative_evaluation(descriptions=args.descriptions, timestamp=args.timestamp)
    else:
        evaluator.run_full_evaluation(descriptions=args.descriptions, timestamp=args.timestamp if hasattr(args, 'timestamp') else None)


if __name__ == "__main__":
    main()