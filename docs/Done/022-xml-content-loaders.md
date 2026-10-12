# Feature: XML Content Loaders (Items, Monsters, Vocations & Spells)

## 1. Explanation
Parses content definitions from Canary's XML files:
- `data/items/items.xml`: Extended item stats (attack, defense, armor, slotPosition, decayTo, duration, charges).
- `data/XML/vocations.xml`: HP/MP gain per level, capacity gain, soul gain, attack speed formulas.
- `data/XML/outfits.xml` & `data/XML/spells.xml`: Available outfits/addons and base spell metadata.
- `data/monster/*.xml`: Monster definitions (looktype, corpse, attacks, defenses, immunities, loot).

## 2. Impact
Provides actual RPG stats, balance, and creature behavior templates. Without this, items have no attack/defense stats, players do not gain vocation-specific vitals, and monsters lack loot tables or spell sets.

## 3. Functionality Affected
- **Item Decoration**: Augments basic OTB items with XML attributes (e.g. Magic Sword having 48 Atk, 35 Def, 1-handed Sword).
- **Vocation Registry**: Dictates Sorcerer, Druid, Paladin, Knight stat curves and regeneration rates.
- **Monster Type Registry**: Monster templates used when spawning monsters on the map.

## 4. Agent Implementation Details
- Implement `XmlItemLoader.kt` using standard Java XML DOM / StAX streaming parser.
- Implement `VocationLoader.kt` building `Vocation` models with stat progression calculations.
- Implement `MonsterTypeLoader.kt` creating `MonsterType` templates with spell lists and loot items.

## 5. Test Reference
- `org.opentibia.canary.io.XmlContentLoaderTest`: Parses a sample XML snippet for items and vocations, asserting attack/defense and vocation stat gains per level.


## Canary Original Code Reference
- **Source Files**: 
- **Canary Repository Root**: 
