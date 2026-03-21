# Brazilian Serie A — XGBoost Match Outcome Predictor

Predicts the result of Brazilian Serie A matches (Home win / Draw / Away win) using bookmaker odds and rolling team form features.

## Dataset

`BRA.csv` is included in this folder. The notebook reads it from the same directory as the notebook file. It covers Serie A seasons from 2012 to 2025.

To update the dataset with the latest matches, download a fresh copy from [football-data.co.uk](https://www.football-data.co.uk/brazil.php) and replace `BRA.csv` in this folder.

**Key columns used:**

| Column | Description |
|--------|-------------|
| `Date` | Match date |
| `Home` / `Away` | Team names |
| `HG` / `AG` | Goals scored by each team |
| `Res` | Result: `H` (home win), `D` (draw), `A` (away win) |
| `PSCH/PSCD/PSCA` | Pinnacle closing odds (home / draw / away) |
| `AvgCH/AvgCD/AvgCA` | Market average odds |
| `MaxCH/MaxCD/MaxCA` | Best available odds across bookmakers |

## Dependencies

```bash
brew install libomp            # required by XGBoost on macOS
pip install xgboost seaborn    # if not already installed
```

All other dependencies (`pandas`, `numpy`, `matplotlib`, `scikit-learn`) are standard and likely already present.

## Running the Notebook

Open and run all cells in order:

```bash
jupyter notebook notebooks/bra_serie_a/bra_serie_a.ipynb
```

Or execute headlessly:

```bash
jupyter nbconvert --to notebook --execute notebooks/bra_serie_a/bra_serie_a.ipynb \
  --output bra_serie_a.ipynb --output-dir notebooks/bra_serie_a/
```

## Notebook Structure

| Section | What it does |
|---------|-------------|
| **1. Setup** | Imports libraries |
| **2. Load Data** | Reads `BRA.csv`, parses dates, sorts chronologically |
| **3. EDA** | Result distribution, average goals per season, odds availability |
| **4. Feature Engineering** | Implied probabilities from odds + rolling team form (last 5 matches) |
| **5. Train/Test Split** | Chronological 80/20 split — no shuffle to avoid leakage |
| **6. XGBoost Model** | Trains a multiclass classifier (`multi:softprob`) |
| **7. Evaluation** | Accuracy, classification report, confusion matrix, feature importances |
| **8. Prediction Example** | Shows predicted probabilities for a single match |

## Features

**Implied probabilities** — converted from decimal odds then normalised to remove the bookmaker margin:

```
implied_prob_home = (1 / home_odds) / (1/home + 1/draw + 1/away)
```

**Rolling team form** (last 5 matches, no data leakage via `shift(1)`):
- Points per game
- Goals scored per game
- Goals conceded per game
- Difference between home and away team for each metric

## Results

Tested on the most recent 20% of matches (~1,050 games):

| Class | Precision | Recall | F1 |
|-------|-----------|--------|----|
| Away win (A) | 0.49 | 0.30 | 0.38 |
| Draw (D) | 0.37 | 0.14 | 0.21 |
| Home win (H) | 0.54 | 0.84 | 0.66 |
| **Overall accuracy** | | | **51.7%** |

Baseline (always predict home win): **48.0%**

Draws are the hardest class to predict — this is consistent with the broader football analytics literature.
