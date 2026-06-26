# Snake Breeder — Detailed Systems Design

> **Design target:** ~**0.9/1 realism** to real-life ball-python / colubrid keeping and genetics —
> but **time-accelerated** (no two-month waits) and with an **eased, forgiving economy** so it's *fun*.
> Deliberately simple "bad" pixel graphics; all the depth is in these systems. Snakes only for now.

This document details every simulation system. It expands the high-level plan
(`/root/.claude/plans/...`) — that file has the tech stack and roadmap; this file is the **rules of the
world**.

---

## 0. Two-layer time model (realistic ratios, compressed wall-clock)

The trick to "realistic but fast": keep all the **ratios** true to life, then compress **wall-clock**.

- **Game clock**: the world runs on **game-days**. Aging, hunger, sheds, growth, incubation, and breeding
  seasons are all measured in game-days using realistic counts (see each system).
- **Speed control**: player sets play speed — **Pause / 1× / 4× / 16×** — and can **"skip to next event"**
  (next feed due, next shed, next pip). One game-day at 1× ≈ a few real seconds; the whole world is
  experienced in **minutes-to-hours, not months**.
- **Realism slider (optional, later)**: a global `timeScale` multiplier and per-system multipliers
  (`incubationScale`, `growthScale`, …) all default to "compressed but proportional." A purist can dial
  toward true real-world lengths; a casual player can speed it up. Default = **fun-fast**.

> Rule of thumb: **proportions are sacred, absolute durations are negotiable.** A shed should still take
> ~1/5 the time between two feeds; incubation should still be ~10× a feeding interval — just measured in
> game-days that fly by.

| Real life | Realistic ratio kept | Default game-days (fun-fast) |
|---|---|---|
| Hatchling feed interval ~5–7 days | baseline "1 feed cycle" | **3 game-days** |
| Adult feed interval ~10–14 days | ~2× hatchling | **6 game-days** |
| Shed cycle (growing) ~4–6 weeks | ~5–7 feed cycles | **18 game-days** |
| "In blue"/refusal window ~7–10 days | ~1.5 feed cycles | **4 game-days** |
| Egg incubation ~55–60 days @ ~89°F | ~9–10 feed cycles | **20 game-days** |
| Female sexual maturity ~2–3 yrs / weight-gated | the long one | weight-gated, ~**120 game-days** |

(All numbers are tunable constants in one config file, not hard-coded.)

---

## 1. Species (real, data-driven)

Phase-1 ships **3 real species** so genetics variety is real on day one. Each is a data file; stats are
real-world-grounded.

| Species | Role | Adult size | Hatchling | Temperament | Diet | Clutch | Notes |
|---|---|---|---|---|---|---|---|
| **Ball Python** (`Python regius`) | flagship, deep morph tree | ♀ 3–5 ft / 1200–1800g, ♂ smaller | ~50–70g, 10" | very docile | rats (size-matched) | 1–11 (avg 4–8) | famous **food refusers**; huge morph market |
| **Corn Snake** (`Pantherophis guttatus`) | hardy beginner, simple recessives | 3–5 ft, slender | 10–15g | docile, active | mice | 10–30 | reliable eater, fast grower |
| **Western Hognose** (`Heterodon nasicus`) | small, quirky, "plays dead" | ♀ ~2–3 ft, ♂ tiny | ~5–8g | dramatic bluffer | mice | 4–20 | small prey, fun behaviors |

Each `SpeciesResource` carries: adult size range, hatchling size, growth curve, **sexual-maturity gate
(weight + age)**, feed interval by life stage, prey type & size rules, shed interval, **breeding season
window**, clutch-size range, incubation game-days & target temp/humidity, temperament, base value, and its
**locus list** (its morph genes).

---

## 2. Genetics — real reptile-morph rules (the core)

Diploid, Mendelian, data-driven. Mirrors how real breeders think.

### 2.1 Dominance types (all three, real examples)
- **Recessive** — needs **two copies** to show; one copy = invisible **"het"** carrier.
  Ball python examples: **Albino, Piebald, Clown, Lavender Albino, Ghost/Hypo, Ultramel**.
  Corn: **Amelanistic, Anerythristic, Charcoal, Lavender** (Snow = amel + anery).
- **Co-dominant / incomplete-dominant** — **one copy** shows a morph, **two copies = a distinct "super"**.
  Ball python: **Pastel → Super Pastel**, **Mojave**, **Lesser**, **Butter**, **Yellowbelly → Ivory**,
  **Fire → Black-Eyed Leucistic**, **Cinnamon/Black Pastel → Super (melanistic)**.
- **Dominant** — one copy shows; two copies look the same. Ball python: **Pinstripe, Enchi (acts co-dom-ish)**.
- **"Special" cases (realism + ethics):**
  - **Spider** — inherited as dominant-acting, comes with a real **neurological "wobble"**; **homozygous
    Spider is widely believed lethal** (no confirmed Super Spider). We model: Spider always carries a
    *wobble severity* trait, and **Spider×Spider loses the homozygous quarter** (eggs fail). This is a real,
    debated welfare issue — included as an **ethics/quality flag** the game surfaces.
  - **Super-form leucistics via combos** — e.g. **Mojave + Lesser = Blue-Eyed Leucistic (BEL)**; the engine
    produces these **emergently** from allele stacking, no hand-authoring.

### 2.2 Genotype → phenotype
- Genotype = for each locus, an **allele pair** (one per parent).
- Resolve each locus by its dominance rule → a set of **expressed morph traits** → **stack** them into the
  final look: base color, pattern type, pattern/blush colors, melanin/xanthin modifiers, eye color,
  pattern-reduction, etc.
- Stacked genes = **designer combos** (Bumblebee = Pastel + Spider; Banana Pied; etc.) appear automatically.

### 2.3 Het tracking & honesty (real-market realism)
- Carriers are tracked as **"het Albino," "66% het," "100% het," "poss. het"** exactly like real ads.
- A snake's *visible* phenotype hides its hets → **breeding is the only way to prove out hets** → drives the
  collect/breed/gamble loop. Selling can disclose "100% het" (proven) vs "66% poss het" (unproven from a
  known pairing) — and the **marketplace later can reward honesty / punish misrepresentation**.

### 2.4 Inheritance & Punnett preview
- Each parent passes **one random allele per locus** (independent assortment; no linkage in slice).
- Before pairing, show a **Punnett odds table** ("12.5% Albino, 25% het Albino Pastel, …").
- **Sex**: snakes are **GSD (ZW)** — genetic, **not** temperature-dependent. (Note: many *other* reptiles
  are TSD; we keep that hook for Phase 3 species but snakes stay GSD.)

### 2.5 "Genetic perfection" / quality (polygenic, line-bred)
- Separate from on/off morphs: hidden **quantitative quality loci** sum to a **Genetic Quality 0–100**
  (expression cleanliness, color saturation, size potential, fertility, vigor).
- **Line-breeding** (selecting high-quality parents) raises offspring quality over generations — like real
  breeders refining "high-contrast" or "high-white" lines.
- **Inbreeding coefficient**: lineage (parent IDs back several gens) is tracked; close pairings raise
  inbreeding → **lowers quality, fertility, hatch rate, vigor**. Realistic trade-off vs. fixing a recessive.

---

## 3. Feeding — real husbandry, with refusals

Feeding is a **decision**, not a guaranteed button.

- **Buy prey** from the shop, sized to the snake: **mouse pinky → fuzzy → hopper → adult**, **rat pup →
  weaned → small → medium → large**. Rule: prey ≈ **1–1.25× the snake's widest girth**. Too big →
  **refusal or regurgitation**; too small → less growth.
- **Frequency** by life stage (game-days from §0). Feeding too often = **"power-feeding"** → faster growth
  but **health/longevity/quality penalty** (realistic and discouraged).
- **Refusal triggers (a snake may decline a meal):**
  - **In shed / "blue" phase** (eyes cloudy) — common, expected.
  - **Recently fed** (still digesting).
  - **Breeding season** (esp. ball python males go off-feed for months IRL → game-weeks).
  - **Stress**: newly acquired/un-acclimated, over-handled, wrong temps/humidity, no hide.
  - **Temperature too low** to digest.
  - **Ball pythons**: baseline higher refusal rate (their reputation) — tunable per species.
- **Regurgitation**: handling too soon after a meal, prey too large, or temps too low → regurge → health
  hit + must rest before retry.
- **Live vs frozen-thawed (F/T)**: F/T (default, safer) vs live (cheaper/sometimes accepted by picky
  feeders but small injury risk) — a realistic shop choice; can be simplified in slice.
- **Assist/force-feeding** (last resort for a long faster) — stressful, risky; an advanced action.

Each feed updates **hunger, weight, growth progress, and care score**.

---

## 4. Shedding, health & husbandry

- **Shed cycle** (§0 cadence): snake goes **"in blue"** (dull skin, cloudy eyes) → refuses food, hides more
  → sheds. **Humidity** governs shed quality:
  - Good humidity → clean single-piece shed.
  - Low humidity → **dysecdysis**: stuck shed, **retained eye caps**, stuck tail tip → health/quality hit
    until remedied (humidity box / soak).
- **Enclosure husbandry parameters** the player manages (per terrarium): **warm-side & cool-side temp
  (gradient), humidity, hide(s), water bowl, substrate**. Wrong values cause realistic problems:
  - Too cold → poor digestion, refusals, **respiratory infection (RI)** risk.
  - Too hot/dry → dehydration, bad sheds.
  - Too humid + poor ventilation → **scale rot / RI**.
  - No hide → chronic stress → refusals, lower care score.
- **Ailments (realistic, husbandry-caused):** **RI, mites, scale rot, retained shed, dehydration,
  obesity (from power-feeding)**. Each has visible signs, a cause, and a fix (correct husbandry, quarantine,
  "vet"/meds item). Untreated → declining health → permanent quality loss → death if neglected badly.
- **Quarantine**: new animals should be isolated; skipping it risks **mite spread** across your collection
  (a real keeper rule, makes a fun risk mechanic).

---

## 5. Growth & life stages

- Stages: **Hatchling → Juvenile → Subadult → Adult**, by **weight + age** (weight is king, like IRL).
- **Growth each game-day** = base curve × food intake × temperature factor × health × care, capped by
  **`min(genetic max size, enclosure size cap)`**.
  - **Genetics** set the ceiling; **care** decides how much of it is realized; the **enclosure** can cap it
    (a snake in too small a tank won't reach full size — your "grows to size of the tank" rule).
- **Power-feeding** reaches size faster but penalizes longevity/quality (discouraged, realistic).
- **Sexual maturity** unlocks breeding only past the species' **weight + age gate** (♀ ball python
  ~1200–1500 g; ♂ much earlier) — prevents unrealistic early breeding.

---

## 6. Breeding & reproduction (seasonal, real flow)

Realistic cycle, accelerated:
1. **Season window**: each species has a breeding season (cooler months). A mild **"cooling/brumation"**
   period can be applied to cue breeding (optional husbandry step → higher success).
2. **Pairing / "locking"**: introduce a mature, healthy ♂ + ♀ → courtship → lock. Multiple pairings raise
   odds. Males often **go off-feed** during season (realistic refusal).
3. **Follicle development → ovulation** (visible mid-body swell) → **pre-lay shed** (a tell that eggs come
   in ~1 cycle).
4. **Gravid female** lays a **clutch** (count from species range × female size/condition × fertility).
   Some eggs may be **"slugs"** (infertile, yellow) — more if inbred / poor condition / young female.
5. **Genetics**: each viable egg's genotype = Mendelian draw from both parents (§2.4). **Spider×Spider**
   loses the homozygous quarter (failed eggs).
6. Female loses condition after laying → **needs recovery** (feeding back up) before next season — prevents
   spamming clutches (realistic + balances economy).

---

## 7. Incubation (player-run, care matters)

- Move eggs to the **incubator**; set **temperature & humidity**.
- **Incubation length** ≈ §0 (default ~20 game-days), **shorter at higher temp, longer at lower** (real).
- **Care during incubation** affects outcome:
  - On-target temp/humidity → high hatch rate, full genetic quality realized.
  - Off-target / neglected → **lower hatchling quality, deformities, or dead-in-egg**.
  - Too hot → faster but **kinks/defects**; too cold → slow, can fail.
- **Candling** to check fertility/development; near term eggs **"dimple"** then **pip**; player can
  **assist-cut** late eggs (risky if too early).
- **Maternal incubation** option (female coils on clutch) — lower management, slightly lower yield —
  realistic alternative to the machine.
- (Snakes = **GSD**, so incubation temp does **not** set sex. The TSD hook stays for future reptiles.)

> **Care score, defined:** a 0–100 rolling stat per animal/egg fed by feeding regularity, husbandry
> correctness, health events, handling stress, and incubation conditions. **Realized quality =
> geneticQuality × f(careScore)** → a genetically elite snake raised badly turns out mediocre; great care
> realizes its full potential. This is the heart of "neglect lowers quality."

---

## 8. Handling & live behavior

- **Pick up / carry**: lift a snake to move it between terrariums, to the incubator/quarantine, or just to
  handle. **Regular gentle handling** slightly tames temperament; **over-handling**, or handling **in shed /
  right after feeding**, adds **stress → refusals/regurge**. Don't handle gravid females.
- **Live enclosure AI**: snakes **wander, explore, hide, bask on the warm side, soak before a shed, coil in
  the hide**. Simple state machine (Hungry/Exploring/Basking/Hiding/InShed/Digesting) so terrariums feel
  alive, not static. Behavior reflects state (a hungry snake prowls; a digesting one hides).

---

## 9. Economy — eased & fun (forgiving on purpose)

Realistic *structure*, generous *tuning*. You should feel progression, not anxiety.

- **Start**: pick "Snake Breeder" → **starter cash** (enough for setup + a comfortable buffer) + **starter
  kit**: 1 terrarium (with basic heat/humidity), basic incubator, a few feeders, and **1–2 starter snakes**
  (a het pair, so breeding is immediately interesting).
- **Income**: sell **common/normal & low-tier morph** hatchlings to the **in-game shop** for steady cash;
  **rare/high-value** animals are **shop-blocked → marketplace/trade only** (Phase 4). Breeding good morphs
  is the path to wealth.
- **Costs**: feeders (cheap), enclosures & equipment (one-time), husbandry consumables, occasional
  vet/meds. **Slice keeps it light** — no electricity bills, no rent spiral, no "game over by bankruptcy."
  (Optional realism toggles for utilities/vet later.)
- **Value formula**: `value = speciesBase × morphMultiplier(stacked, rarity) × qualityFactor ×
  conditionFactor(age/health) × hetBonus(proven)`. Rarer stacked recessives + high quality = big multipliers
  → rewards skilled breeding.
- **Easing knobs** (all config): higher base sale prices, cheap food, forgiving refusal/illness rates by
  default, no permadeath from a single mistake (health recovers with care). A future **"Hardcore/Realism"**
  difficulty tightens all of these toward 1.0/1 realism.

---

## 10. What this means for Phase 1 (slice) vs later

- **In the slice (built around these rules, tuned light):** 3 species, full morph genetics + hets + quality
  + inbreeding, feeding **with refusals & prey sizing**, **shed cycle + basic husbandry (temp/humidity/hide)**,
  growth + life stages + maturity gate, seasonal-ish breeding → clutch (with slugs) → **player incubation
  with care effects** → hatch, **handling/carry**, **live idle AI**, eased economy, save/load.
- **Deferred (Phase 2+):** full ailment list & vet depth, brumation detail, live-vs-F/T nuance, utilities
  economy, more species/morphs, other animal classes, and all online/marketplace/trading/premium-currency.

---

## 11. Tuning constants live in ONE place

Every number above (feed intervals, shed/incubation game-days, refusal probabilities, maturity weights,
prices, quality/inbreeding weights, time scales) is a field in a single **`balance` config** so the whole
game can be re-tuned from "fun-fast/easy" to "0.9-realistic" without touching logic. Default profile =
**Fun-Fast / Eased**; a **Realism profile** ships later.
