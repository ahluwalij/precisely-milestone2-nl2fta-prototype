# NL2FTA Evaluation Framework

A comprehensive framework for evaluating semantic types using FTA classification with pre-generated type definitions.

## Prerequisites

- **Docker**: Engine installed and running (Linux: system service; macOS: Docker Desktop).
- **Python**: Version 3.9 or higher.
- **Available RAM**: At least 4GB.
- **Note**: The evaluation script automatically creates and manages a Python virtual environment (`.venv-nl2fta`) to avoid system package conflicts.

### Windows users: enable direct `.sh` execution (one-time)

Run this from the repo root to execute bash scripts directly in PowerShell (no WSL):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\windows-enable-bash-scripts.ps1
```

Then reopen PowerShell and call scripts as shown below. If your shell still opens `.sh` in an editor, use the generated `.cmd` wrappers (e.g., `scripts\run-eval.cmd`).

## Quick Start

This evaluation suite is designed to be run entirely offline without requiring real AWS credentials.

1.  **Verify prerequisites**
    Ensure Docker is running and Python 3.9+ is available on PATH.

2.  **Run the Evaluation**
    Execute the main evaluation script. By default, it runs all datasets in comparative mode with all description patterns.

    ```bash
    ./scripts/run-eval.sh
    ```

    To run all datasets with all descriptions at once:

    ```bash
    ./scripts/run-eval.sh --dataset all
    ```

    To run individual datasets:

    ```bash
    # Run a single dataset with auto-detected descriptions
    ./scripts/run-eval.sh --dataset extension
    ./scripts/run-eval.sh --dataset insurance
    # Unified multi-file evaluator for SemTab too. Pass custom subsets via --files and base via --data-dir:
    ./scripts/run-eval.sh --dataset semtab --descriptions "1 2 3 4 5 6"
    ./scripts/run-eval.sh --dataset semtab --files \
      "/abs/path/GitTables_104.csv,/abs/path/dirA,/abs/path/GitTables_38.csv" --debug
    ./scripts/run-eval.sh --dataset semtab --data-dir \
      "/Users/you/custom/semtab_data" --files "GitTables_60.csv,subset_dir"
    ./scripts/run-eval.sh --dataset fintech_bank_transaction --descriptions "1 2 3 4 5 6"
    ./scripts/run-eval.sh --dataset fintech_banking_dataset --descriptions "1 2 3 4 5 6"
    ./scripts/run-eval.sh --dataset telco_5GTraffic --descriptions "1 2 3 4 5 6"
    ./scripts/run-eval.sh --dataset telco_customer_churn --descriptions "1 2 3 4 5 6"
    ```

    Common recipes:

    - Extension dataset, description 6
      ```bash
      ./scripts/run-eval.sh --dataset extension --descriptions "6" --verbose
      ```
    - Telco churn, subset of descriptions
      ```bash
      ./scripts/run-eval.sh --dataset telco_customer_churn --descriptions "1 2 3"
      ```
    - Use full dataset rows (disable default truncation)
      ```bash
      ./scripts/run-eval.sh --dataset telco_5GTraffic --descriptions "6" --full-data
      ```

## CLI quick reference

### scripts/run-eval.sh

Purpose: run the offline evaluator against local CSVs with pre-generated semantic types.

Common usage:

```bash
./scripts/run-eval.sh \
  --dataset YOUR_DATASET \
  --descriptions "1 2 3 4 5 6" \
  --data-dir /abs/path/to/your/csvs \
  --timestamp 20250811_170954
```

Examples:

```bash
# Minimal (auto-discovers evaluator/datasets/data/<dataset>)
./scripts/run-eval.sh --dataset extension

# Specific files/dirs (comma-separated); pin to a generated-types timestamp
./scripts/run-eval.sh --dataset insurance \
  --descriptions "1 2 3" \
  --files "/abs/path/policy.csv,/abs/path/folder" \
  --timestamp 20250811_170954

# SemTab subset with debug logging and larger heap
./scripts/run-eval.sh --dataset semtab \
  --descriptions "6" \
  --files "/data/semtab/GitTables_104.csv,/data/semtab/subset" \
  --heap 3g --debug

# Full rows (disable default truncation)
./scripts/run-eval.sh --dataset telco_customer_churn --full-data
```

Flags:

- `--dataset NAME` — logical tag controlling which generated types to load and the output names
- `--descriptions "LIST"` — which description profiles to run (default: auto-detect from generated files; falls back to "1 2 3 4 5 6" if none detected)
- `--timestamp TS` — pin the generated-type version for all selected descriptions (format: `YYYYMMDD_HHMMSS`)
- `--files LIST` — comma-separated absolute paths to CSV files and/or directories
- `--data-dir DIR` — directory to scan for CSVs when `--files` is omitted
- `--heap SIZE` — shorthand for Java `-Xmx` (and sets `-Xms` ≈ 50% of `-Xmx`)
- `--full-data` — do not truncate rows (default truncation is enabled for reliability)
- `--verbose` — print all script and evaluator output
- `--debug` — enable debug-level logs in the Python evaluator
- `--no-cleanup` — leave the backend container running after completion
- `--help` — show usage

Notes:

- `--timestamp` applies to the evaluator only. It selects which generated-type JSONs to use from `evaluator/generated_semantic_types/`.
- If both `--files` and `--data-dir` are omitted, the script will scan `evaluator/datasets/data/<dataset>`.

Outputs:

- Per-run directory: `evaluator/logs/<timestamp>/`
- Log: `run-eval.log` (single consolidated log)
- Results: `<dataset>_profile_results_<timestamp>.json`
- Final summary: `final_summary.md` and `final_summary.csv`

### scripts/generate-semantic-types.sh

Purpose: call the backend’s Bedrock-powered generator to produce timestamped semantic type JSON files that the evaluator consumes.

Common usage:

```bash
./scripts/generate-semantic-types.sh \
  --dataset YOUR_DATASET \
  --descriptions "1 2 3 4 5 6" \
  --data-dir /abs/path/to/your/csvs \
  --region us-east-1
```

Examples:

```bash
# Generate all descriptions for a dataset (interactive AWS creds if not provided)
./scripts/generate-semantic-types.sh --dataset extension --descriptions "1 2 3 4 5 6"

# Point to explicit files/dirs (comma-separated) to ground generation
./scripts/generate-semantic-types.sh --dataset semtab \
  --files "/abs/path/GitTables_104.csv,/abs/path/subdir,/abs/path/GitTables_38.csv"

# Non-interactive (credentials via env) and larger heap
AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=... AWS_REGION=us-east-1 \
  ./scripts/generate-semantic-types.sh --dataset telco_5GTraffic --descriptions "6" --heap 3g

# Keep generator backend running
./scripts/generate-semantic-types.sh --dataset insurance --no-cleanup
```

Flags:

- `--dataset NAME` — dataset tag used in output filenames
- `--descriptions "LIST"` — which description profiles to generate (e.g., "6" or "1 3 6")
- `--data-dir DIR` — directory to scan for CSVs (required if `--files` omitted)
- `--files LIST` — comma-separated absolute paths to CSV files and/or directories
- `--region REGION` — AWS region for Bedrock (default: `us-east-1`)
- `--heap SIZE` — shorthand for Java `-Xmx` (and sets `-Xms` ≈ 50% of `-Xmx`)
- `--no-cleanup` — leave the generator backend running after completion
- `--use-saved-credentials` — use saved AWS credentials without prompting
- `--help` — show usage

Notes:

- **About `--use-saved-credentials`**: This flag automatically uses previously saved AWS credentials without prompting. If no saved credentials exist, the script will exit with an error message explaining how to set up credentials first. Run the script without this flag initially to save your credentials interactively.
- The generator writes outputs as: `evaluator/generated_semantic_types/{dataset}_descriptionN_{YYYYMMDD_HHMMSS}.json`.
- That same run timestamp is shown in the log path `evaluator/logs/<timestamp>/generate-types.log`.
- To pin the evaluator to one of these generated runs, pass the timestamp to `run-eval.sh` via `--timestamp <YYYYMMDD_HHMMSS>`.
- There is no `--timestamp` flag on the generator; timestamps are assigned automatically per run.

Outputs:

- Generated types: `evaluator/generated_semantic_types/{dataset}_descriptionN_{timestamp}.json`
- Logs: `evaluator/logs/<timestamp>/generate-types.log`

### Validate datasets before running

Use the validator to check that your dataset directories and CSVs follow the universal format and to review ground-truth mappings per column:

```bash
# Validate every dataset directory under evaluator/datasets/data/
./scripts/validate-datasets.sh

# Validate a single dataset directory
./scripts/validate-datasets.sh --dataset extension
```

Expected CSV layout (universal):

- Row 0: built-in ground truth (baseline)
- Row 1: custom ground truth
- Row 2: headers
- Row 3+: data rows

The script outputs clean summaries and shows, for each column, baseline vs custom labels for quick manual verification.

### Generate semantic types (LLM-backed)

Use the CLI generator to produce custom semantic types for a dataset using AWS Bedrock via the backend API. Outputs are timestamped JSON files the evaluator can consume directly.

What it does

- Spins up a dedicated backend on port 8083 (separate from eval/dev)
- Prompts for or uses provided AWS credentials; validates with STS and (optionally) Bedrock
- Calls the backend’s generation endpoints according to selected descriptions
- Saves outputs to `generated_semantic_types/` and a full run log in `logs/<timestamp>/`
- Crash-safe incremental writes: each description file is updated after each generated type, preserving partial progress
- Stable per-run timestamp: all files from one generator run share the same start-time timestamp

Outputs

- `generated_semantic_types/{dataset}_descriptionN_{YYYYMMDD_HHMMSS}.json`
- `logs/<timestamp>/generate-types.log`

Notes on versioning and selection during evaluation

- Use `--timestamp YYYYMMDD_HHMMSS` with the evaluator to load the exact generated files from a specific generator run.
- Because all files in one generator invocation carry the same timestamp, you can reliably pin an evaluation to that run.

Usage

```bash
# Basic: generate all description sets for a dataset
./scripts/generate-semantic-types.sh --dataset extension --descriptions "1 2 3 4 5 6"

# Use a specific data directory to ground the generation
./scripts/generate-semantic-types.sh --dataset insurance --data-dir evaluator/datasets/data/insurance

# Point to explicit files and subdirectories (comma-separated)
./scripts/generate-semantic-types.sh --dataset semtab \
  --files "/abs/path/GitTables_104.csv,/abs/path/subdir,/abs/path/GitTables_38.csv"

# Non-interactive with AWS credentials via env
AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=... AWS_REGION=us-east-1 \
  ./scripts/generate-semantic-types.sh --dataset telco_5GTraffic --descriptions "6"

# Heavier runs: increase backend heap
./scripts/generate-semantic-types.sh --dataset extension --descriptions "6" --heap 3g

# Keep backend alive after generation (debugging)
./scripts/generate-semantic-types.sh --dataset extension --no-cleanup
```

Flags

- `--dataset NAME` — dataset tag used in output filenames
- `--descriptions "LIST"` — description profiles to generate (optional; if omitted, the generator auto-detects available description indices from the inputs CSV header: `Generation Description <n>`) 
- `--data-dir DIR` — directory containing CSVs (if `--files` not provided)
- `--files LIST` — comma-separated paths (files or directories)
- `--region REGION` — AWS region (default `us-east-1`)
- `--no-cleanup` — leave backend running

How evaluator consumes outputs

- Evaluator auto-selects the most recent `generated_semantic_types/{dataset}_descriptionN_{timestamp}.json` when multiple versions exist and logs which one it picked.
- To pin deterministically to a specific version, run the evaluator with `--timestamp YYYYMMDD_HHMMSS`.

3.  **View Results & Logs**
    The runner writes the consolidated log and JSON results into `evaluator/logs/<timestamp>/`.
    ```bash
    # View the latest run folder
    RUN_DIR=$(ls -td evaluator/logs/*/ | head -1)
    echo "Latest run dir: $RUN_DIR"
    # Inspect the live log and a sample JSON
    ls -l "$RUN_DIR" | sed -n '1,50p'
    # Example: show the evaluation log
    tail -n 200 "$RUN_DIR"/run-eval.log
    ```

### Unified multi-file evaluator

All datasets now use the same evaluator and accept a list of files and/or directories. Row conventions per CSV:

- Row 0: ground truth for built-in types (baseline)
- Row 1: ground truth for custom types
- Row 2: column headers
- Row 3+: data rows

All outputs are written to the per-run directory (see above).

#### Dataset and inputs resolution

- `--dataset NAME` sets the dataset tag. If you do not pass `--data-dir` or `--files`, the evaluator will automatically scan `evaluator/datasets/data/NAME` for CSVs.
- The dataset tag controls which generated types and labels are used:
  - Generated types: `evaluator/generated_semantic_types/{NAME}_descriptionN_*.json` (see timestamp rules below)
  - Labels: `evaluator/datasets/generation-labels/{NAME}_labels.csv` (used only to generate custom types; never for ground truth)
- If you do pass `--data-dir`/`--files`, those only affect where CSVs come from; the dataset tag still determines which types/labels are loaded.

### Understanding the Result Filename

The result files are named using a consistent format that includes the dataset, a `profile_results` identifier, and a timestamp.

- **Format**: `{dataset_name}_profile_results_{timestamp}.json`
- **Example**: `insurance_profile_results_20250811_170954.json`

The timestamp is formatted as `YYYYMMDD_HHMMSS`, allowing you to easily sort and identify the latest results.

## How It Works

The evaluation system is completely self-contained:

1.  **Isolated Backend**: Starts a separate backend instance on port 8082.
2.  **Python Virtual Environment**: Automatically creates and activates a virtual environment (`.venv-nl2fta`) to manage Python dependencies.
3.  **Offline Mode**: Uses dummy AWS credentials, as no cloud access is needed.
4.  **Pre-Generated Types**: Loads semantic type definitions from local JSON files in `evaluator/generated_semantic_types/`.
5.  **Local Analysis**: Runs FTA classification on local test datasets in `evaluator/datasets/data/` (SemTab CSVs can also be provided via `--data-dir`/`--files`).
6.  **Metrics Calculation**: Computes accuracy, precision, recall, and F1 scores.
7.  **Per-run Artifacts**: Saves all artifacts for each run into `evaluator/logs/<timestamp>/` including a consolidated `run-eval.log` and `{dataset}_profile_results_<timestamp>.json`.

## Usage and Customization

You can customize the evaluation run using command-line flags.

### Selecting a Dataset

Use the `--dataset` flag to specify which dataset to use.

```bash
./scripts/run-eval.sh --dataset telco_5GTraffic
```

**Included Datasets:**

| Dataset                    | Description            | Columns | Semantic Types |
| -------------------------- | ---------------------- | ------- | -------------- |
| `extension`                | Diverse semantic types | 124     | 62             |
| `telco_5GTraffic`          | Telecom network data   | 14      | 14             |
| `telco_customer_churn`     | Customer churn data    | 26      | 26             |
| `fintech_bank_transaction` | Banking transactions   | 19      | 19             |
| `fintech_banking_dataset`  | Banking dataset        | 18      | 18             |
| `insurance`                | Insurance policy data  | 30      | 30             |
| `semtab`                   | SemTab benchmark data  | 6900+   | Various        |
| `all`                      | Run all datasets       | -       | -              |

### Selecting Description Patterns

Use the `--descriptions` flag to run specific pre-generated type sets.

```bash
# Run with description patterns 1, 3, and 5
./scripts/run-eval.sh --descriptions "1 3 5"
```

The descriptions are defined as follows.

- **P1 Description**: Entry from a business-defined glossary of semantic types. One to two sentences long + the two positive examples for each type.
- **P2 Description**: Specific, with instructions on what the semantic type entries look like and without cell examples. No description of a regex. Two sentences long.
- **P3 Description**: Specific, with instructions on what the semantic type entries look like and with two cell examples. No description of a regex. Two sentences long.
- **P4 Description**: Specific, with instructions on what the semantic type entries look like and with two cell examples. Short description of a regex. Two sentences long.
- **P5 Description**: Short description, with header name (does not exactly match the column name) and informal description of the pattern. The header name should not be the same as the actual column header name. No regex and no examples. One sentence long.
- **P6 Description**: Short description, with header name (does not exactly match the column name) and informal description of the pattern. No regex. One sentence long. Two positive and two negative cell examples.

### Evaluation Mode

The evaluator always runs in comparative mode (custom types vs baseline built-ins). The `--mode` flag has been removed.

### Other Options

- `--debug`: Enables detailed debug-level logging.
- `--verbose`: Shows all script output, including suppressed warnings.
- `--no-cleanup`: Keeps the backend container running after the evaluation finishes.
- `--full-data`: Disable default row truncation; send full dataset to backend.
- `--help`: Displays the full list of available options.

### Versioning of generated types (timestamps)

- By default, the evaluator auto-selects the most recent timestamped file for each description.
- Use `--timestamp YYYYMMDD_HHMMSS` to force a specific version for all selected descriptions.
- If no timestamped files exist, it falls back to legacy `..._descriptionN.json`.

Examples:

```bash
# Disambiguate to a specific generated types version
./scripts/run-eval.sh --dataset insurance \
  --descriptions "1 2 3" \
  --timestamp 20250811_170954

# If multiple datasets are evaluated (including --dataset all), any ambiguity
# in any dataset/description requires a timestamp or the run will fail early.
```

## Understanding the Output

A typical evaluation run will produce the following output:

```
╔══════════════════════════════════════════════════════════════════╗
║               NL2FTA Evaluation Suite                            ║
╚══════════════════════════════════════════════════════════════════╝

✔ Prerequisites satisfied
Starting evaluation backend on port 8082...
Waiting for backend to start........
✔ Backend is ready

═══════════════════════════════════════════════════════
Starting Evaluation
═══════════════════════════════════════════════════════
Dataset: extension
Descriptions: 1 2 3 4 5 6
Mode: custom-only

... (Evaluation logs will stream here) ...

✔ Evaluation completed successfully
Results saved to: evaluator/logs/20250811_123456/extension_profile_results_20250811_123456.json

Stopping evaluation backend...
✔ Cleanup complete

═══════════════════════════════════════════════════════
Evaluation complete!
═══════════════════════════════════════════════════════
```

## Troubleshooting

- **Port 8082 Already in Use**: The script will stop any existing containers bound to 8082. Alternatively, stop manually: `docker ps | grep 8082 | awk '{print $1}' | xargs docker stop`.
- **Docker Not Running**: Start Docker and wait for it to initialize. On Linux: `sudo systemctl start docker && sudo systemctl enable docker`.
- **Missing Python Dependencies**: The script automatically creates a virtual environment and installs dependencies. If you encounter issues, delete `.venv-nl2fta/` and re-run the script.
- **Backend Fails to Start**: Check the Docker logs for errors: `COMPOSE_PROJECT_NAME=nl2fta_eval docker-compose -f docker-compose.dev.yml logs backend`.
- **OutOfMemoryError: Java heap space**: Increase heap via `--heap 3g` or a full override with `--jvm-opts "-Xms1g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"`. The script will echo the applied JVM options.

---

## How datasets, generated types, and labels are resolved

When you run the evaluator with `--data-dir` or `--files`, the framework uses a unified multi-file flow:

- **Dataset tag**: Derived from the base directory (with `--data-dir`) or from the parent of the first file (with `--files`). This tag is used to locate generated types.

- **Generated types**: For each description number `N`, the evaluator loads the latest file matching:

  - `evaluator/generated_semantic_types/{dataset}_descriptionN_*.json` (timestamped; picks latest)
  - Falls back to `evaluator/generated_semantic_types/{dataset}_descriptionN.json` if no timestamped file exists.
  - Types with `resultType == "error"` are filtered out before use.

- **Ground truth (GT)** with `--data-dir/--files` (universal CSV format):

  - Row 0: baseline GT (built-in types)
  - Row 1: custom GT
  - Row 2: column headers
  - Row 3+: data rows
  - In comparative mode, the evaluator first runs a baseline pass (row 0 GT), then a custom pass (row 1 GT), and reports metrics and deltas.

- Labels CSVs are not used for ground truth. They are only inputs to generate the custom type JSON files. Ground truth must always come from the data CSV universal layout (row 0 baseline, row 1 custom, row 2 headers, row 3+ data).

## Where logs and results are written

- The runner exports a stable timestamp for the overall run via `EVAL_RUN_TIMESTAMP` and sets `EVALUATOR_RUN_DIR` to `evaluator/logs/<timestamp>`.
- The evaluator writes a single consolidated log file per run:
  - `run-eval.log`
  - `{dataset}_profile_results_<timestamp>.json`
    If `EVALUATOR_RUN_DIR` is not set, the default is `evaluator/logs/<timestamp>/`.

### Environment variables

- `EVALUATOR_API_BASE_URL` — Backend API base (default `http://localhost:8081/api`)
- `EVAL_RUN_TIMESTAMP` — Stable run timestamp used in filenames
- `EVALUATOR_RUN_DIR` — Absolute directory for this run’s logs and results (preferred)
- `EVAL_MODE` — Single switch that enables evaluator defaults (truncation on, row cap 1000, column cap 300, upload mode=file). You can still override at runtime via CLI flags; per-variable overrides are deprecated for handoff.

## Add your own dataset (universal CSV format with ground truth)

Use the universal multi-file format so the evaluator can score both baseline and custom tracks. You can optionally use a labels CSV (documented below) if you don’t want to embed GT rows in your data.

### 1) Pick a dataset tag

- Choose a name: for example `my_company_events`.
- This tag is used to auto-locate both your data files and the labels file.

### 2) Place your data files under `evaluator/datasets/data/<dataset_tag>/`

Example:

- Create a folder: `evaluator/datasets/data/my_company_events/`
- Put one or more CSV files inside. The file names can be anything, e.g. `evaluator/datasets/data/my_company_events/table1.csv`.

CSV universal format (required when you embed ground truth in the CSV):

- Row 0 (first row): baseline ground truth (built-in types)
- Row 1 (second row): custom ground truth (your custom types)
- Row 2: column headers (real column names)
- Row 3+: data rows

Notes:

- Ground truth rows contain semantic type names aligned by column (baseline in row 0, custom in row 1). Any empty/`nan` entries are ignored for that track.
- UTF-8 encoding; commas as separators.
- You can include additional CSVs in the same folder; the evaluator processes all `.csv` files it finds for the dataset.

### 3) (Optional) Use a labels file instead of embedding GT rows

If you prefer not to embed GT rows in the CSV, create a labels file:

- Path: `evaluator/datasets/generation-labels/<dataset_tag>_labels.csv`
- For our example: `evaluator/datasets/generation-labels/my_company_events_labels.csv`

Required columns (minimum):

- `Type`: semantic type identifier (e.g., `EMAIL.ADDRESS`)
- Provide one of (both is best): `Column_Name` (exact header text), `Column_Index` (zero-based)

Recommended (for better generation/eval):

- `Generation Description 1` ... `Generation Description 6`
- `Positive Value Examples`, `Negative Value Examples`
- `Positive Header Examples`, `Negative Header Examples`

**⚠️ Important**: All `Generation Description` fields must be enclosed in **double quotes** ("). This is required for proper CSV parsing and validation. If descriptions contain commas or special characters, they must be properly quoted to avoid parsing errors.

Minimal example:

```
Type,Column_Index,Column_Name
EMAIL.ADDRESS,2,user_email
IDENTIFIER.ORDER_ID,0,order_id
DATE.YYYY_MM_DD,1,order_date
```

Richer example:

```
Type,Column_Index,Column_Name,Generation Description 1,Positive Value Examples,Negative Value Examples,Positive Header Examples,Negative Header Examples
EMAIL.ADDRESS,2,user_email,"User email address (RFC 5322-like typical business emails)","john@example.com; support@company.io","foo; example@","email; primary_email","phone; name"
```

### 4) How matching works (precise rules)

- Dataset tag resolution:
  - If you pass `--data-dir evaluator/datasets/data/my_company_events`, the tag is `my_company_events` (the last folder name).
  - If you pass `--files` with a CSV named `my_company_events_data.csv`, the tag also resolves to `my_company_events`.
- Labels file resolution:
  - The evaluator looks for `evaluator/datasets/generation-labels/<dataset_tag>_labels.csv`.
- Column mapping order of precedence:
  - If `Column_Name` is present, it is matched against the CSV header (case-sensitive exact match is recommended).
  - If `Column_Name` is missing, `Column_Index` is used. Indexing is zero-based.
  - If both are present and disagree, `Column_Name` wins and the index is treated as a sanity check.
- Unmatched labels (columns not found in the CSV header) are ignored with a warning and do not break the run.
- Unlabeled columns in your data are simply not scored.

### 5) Run the evaluator

Simplest: pass just the dataset tag; it will auto-scan `evaluator/datasets/data/<dataset>` for CSVs.

```bash
./scripts/run-eval.sh --dataset my_company_events --descriptions "1 2 3 4 5 6"
```

If you want to point at an arbitrary directory or specific files:

```bash
./scripts/run-eval.sh \
  --dataset my_company_events \
  --descriptions "1 2 3 4 5 6" \
  --data-dir /abs/path/to/my_company_events
```

Notes:

- The evaluator always runs in comparative mode (custom types vs built-ins). The `--mode` flag has been removed.
- Generated types (if you have them) should be named `evaluator/generated_semantic_types/<dataset_tag>_descriptionN_*.json` (the most recent timestamp is auto-selected) or the non-timestamp fallback `..._descriptionN.json`.
- Add `--verbose` to see detailed mapping logs.

### 6) Verify outputs

- Logs and results land in `evaluator/logs/<timestamp>/`:
  - `run-eval.log`: per-run consolidated log
  - `<dataset_tag>_profile_results_<timestamp>.json`: metrics + per-column scoring

If something doesn’t match as expected, confirm your CSV follows the universal row layout (rows 0/1 GT, row 2 headers, row 3+ data). If you used a labels file, check header names in your CSV against `Column_Name`, and verify zero-based `Column_Index`.