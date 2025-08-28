# NL2FTA Evaluation Datasets

This directory contains all datasets used for evaluating the NL2FTA semantic type detection system.

## Directory Structure

```
datasets/
├── README.md            # This file
├── semtab_data/        # SemTab benchmark CSV files (processed via unified multi-file evaluator)
└── data/               # Local evaluation datasets
    ├── extension_data.csv
    ├── extension_labels.csv
    ├── fintech_bank_transaction_data.csv
    ├── fintech_bank_transaction_labels.csv
    ├── fintech_banking_dataset_data.csv
    ├── fintech_banking_dataset_labels.csv
    ├── insurance_data.csv
    ├── insurance_labels.csv
    ├── telco_5GTraffic_data.csv
    ├── telco_5GTraffic_labels.csv
    ├── telco_customer_churn_data.csv
    └── telco_customer_churn_labels.csv
```

## Available Datasets

### 1. Extension Dataset
- **Data**: `extension_data.csv`
- **Labels**: `extension_labels.csv`
- **Types**: 186 semantic types
- **Examples**: CONTINENT.CODE, AIRPORT.IATA, EMAIL.ADDRESS, etc.
- **Use Case**: Comprehensive testing across diverse data types

### 2. FinTech - Bank Transactions
- **Data**: `fintech_bank_transaction_data.csv`
- **Labels**: `fintech_bank_transaction_labels.csv`
- **Types**: 16 semantic types
- **Examples**: TransactionID, AccountNumber, TransactionAmount, etc.
- **Use Case**: Banking transaction analysis

### 3. FinTech - Banking Dataset
- **Data**: `fintech_banking_dataset_data.csv`
- **Labels**: `fintech_banking_dataset_labels.csv`
- **Types**: 18 semantic types
- **Examples**: CustomerID, LoanAmount, CreditScore, etc.
- **Use Case**: General banking data analysis

### 4. Insurance Claims
- **Data**: `insurance_data.csv`
- **Labels**: `insurance_labels.csv`
- **Types**: 30 semantic types
- **Examples**: PolicyNumber, ClaimAmount, CustomerName, etc.
- **Use Case**: Insurance industry data processing

### 5. Telco - 5G Traffic
- **Data**: `telco_5GTraffic_data.csv`
- **Labels**: `telco_5GTraffic_labels.csv`
- **Types**: 4 semantic types
- **Examples**: IMSI, CellID, DataVolume, SessionDuration
- **Use Case**: 5G network traffic analysis

### 6. Telco - Customer Churn
- **Data**: `telco_customer_churn_data.csv`
- **Labels**: `telco_customer_churn_labels.csv`
- **Types**: 26 semantic types
- **Examples**: CustomerID, TotalCharges, Contract, ChurnStatus, etc.
- **Use Case**: Customer churn prediction

## Usage

The evaluation framework uses a single evaluator for all datasets. Provide one or more files or a directory via the runner:

```bash
# Comparative eval over a directory; dataset tag is derived from the directory name
./scripts/run-eval.sh --descriptions "1 2 3 4 5 6" --data-dir "evaluator/datasets/data/fintech_bank_transaction"

# Or over a specific set of files and subfolders
./scripts/run-eval.sh --files "/abs/path/GitTables_104.csv,/abs/path/subdir,/abs/path/GitTables_38.csv"
```

Windows users: run `.sh` directly (one-time setup)

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\windows-enable-bash-scripts.ps1
```

After reopening PowerShell, run the same commands as-is. If `.sh` files open in an editor, use the generated `.cmd` wrappers (e.g., `scripts\validate-datasets.cmd`).

## Validate datasets

Before running evaluations, validate that your dataset directory and CSVs conform to the universal format and inspect ground-truth mappings per column:

```bash
# Validate all dataset directories under evaluator/datasets/data/
./scripts/validate-datasets.sh

# Validate a single dataset directory
./scripts/validate-datasets.sh --dataset extension
```

CSV universal layout (when embedding GT rows):
- Row 0: built-in ground truth (baseline)
- Row 1: custom ground truth
- Row 2: headers
- Row 3+: data rows

SemTab CSVs can be provided via `--data-dir` or `--files` (point to a directory or CSVs).

## Generate semantic types

Create custom semantic types with the generator script. Outputs are timestamped JSONs that the evaluator loads automatically.

```bash
# Generate for a dataset using all description profiles
./scripts/generate-semantic-types.sh --dataset extension --descriptions "1 2 3 4 5 6"

# Use a dataset’s data directory to guide generation
./scripts/generate-semantic-types.sh --dataset insurance \
  --data-dir evaluator/datasets/data/insurance

# Use explicit files and subfolders
./scripts/generate-semantic-types.sh --dataset semtab \
  --files "/abs/path/GitTables_104.csv,/abs/path/subdir,/abs/path/GitTables_38.csv"
```

Outputs
- `evaluator/generated_semantic_types/{dataset}_descriptionN_{YYYYMMDD_HHMMSS}.json`
- `evaluator/logs/<timestamp>/generate-types.log`

### Dataset, inputs, and generated types

- `--dataset NAME` sets the dataset tag.
  - If `--data-dir/--files` are omitted, the evaluator scans `evaluator/datasets/data/NAME` for CSVs.
  - Types and labels are resolved by NAME regardless of input location:
    - Generated types: `evaluator/generated_semantic_types/{NAME}_descriptionN_*.json`
    - Labels: `evaluator/datasets/generation-labels/{NAME}_labels.csv`
- If multiple timestamped generated types exist for any selected description, you must provide `--timestamp YYYYMMDD_HHMMSS`. Without it, the run fails fast.

## Data Format

### Label Files (legacy single-file mode)
- When not using `--data-dir/--files`, the evaluator attempts `{dataset}_labels.csv` first (expects columns like `column_name` and `type`). If absent, it falls back to the universal layout in the dataset CSV (row 0 GT; row 1 headers).

### Data Files (Universal format)
- Row 0: built-in GT
- Row 1: custom GT
- Row 2: headers
- Row 3+: data

### Generated Types Resolution

- For each description N, the evaluator loads the latest of:
  - `evaluator/generated_semantic_types/{dataset}_descriptionN_*.json`
  - fallback: `evaluator/generated_semantic_types/{dataset}_descriptionN.json`
- Types with `resultType == "error"` are filtered out.

### Logs & Results

- The runner sets `EVALUATOR_RUN_DIR=evaluator/logs/<timestamp>`; the Python evaluator writes both the live log and `{dataset}_profile_results_<timestamp>.json` to that directory. If not set, results default to `evaluator/logs/<timestamp>/`.

---

## Add your own dataset (step-by-step)

This is the shortest, reliable way to add your data and labels.

### 1) Choose a dataset tag

- Use lowercase with underscores, e.g. `my_company_events`.

### 2) Put your CSVs here

- Create `evaluator/datasets/data/my_company_events/`
- Place your CSVs inside. The simplest single-table flow is naming the file exactly:
  - `evaluator/datasets/data/my_company_events/my_company_events_data.csv`
- CSV format when labels are provided:
  - First row: headers (real column names)
  - Remaining rows: data
  - Do not embed ground-truth rows at the top when a labels file exists.

### 3) Create the labels file

- Path: `evaluator/datasets/generation-labels/my_company_events_labels.csv`
- Minimum columns: `Type` and either `Column_Name` or `Column_Index` (zero-based). If both are present, `Column_Name` takes precedence.
- Optional columns to improve context: `Generation Description 1..6`, `Positive/Negative Value Examples`, `Positive/Negative Header Examples`.

**⚠️ Important**: All `Generation Description` fields must be enclosed in **double quotes** ("). This is required for proper CSV parsing and validation. If descriptions contain commas or special characters, they must be properly quoted to avoid parsing errors.

Minimal example:

```
Type,Column_Index,Column_Name
EMAIL.ADDRESS,2,user_email
IDENTIFIER.ORDER_ID,0,order_id
DATE.YYYY_MM_DD,1,order_date
```

### 4) How matching works

- Dataset tag resolution:
  - `--data-dir evaluator/datasets/data/my_company_events` → tag is `my_company_events`
  - A file named `my_company_events_data.csv` also implies the tag `my_company_events`
- Labels file: `evaluator/datasets/generation-labels/<tag>_labels.csv`
- Column mapping precedence:
  - `Column_Name` (exact match to CSV header) → preferred
  - else `Column_Index` (zero-based)
  - Conflicts: `Column_Name` wins; index is treated as a hint
- Unmatched labels are ignored with a warning; unlabeled columns are not scored

### 5) Run

```bash
./scripts/run-eval.sh \
  --dataset my_company_events \
  --descriptions "1 2 3 4 5 6" \
  --data-dir evaluator/datasets/data/my_company_events
```

Tips:
- The evaluator always runs in comparative mode (custom types vs baseline)
- Generated types (if any) go under `evaluator/generated_semantic_types/my_company_events_descriptionN_*.json`
- Use `--verbose` for mapping details

### 6) Inspect results

- `evaluator/logs/<timestamp>/run-eval.log`
- `evaluator/logs/<timestamp>/my_company_events_profile_results_<timestamp>.json`