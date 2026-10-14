# Feature: [Feature Name]

## 1. Explanation
[Describe what the feature does, how it works in the OpenTibia universe, and the high-level design in Kotlin.]

## 2. Impact
[Describe the gameplay consequence, system risk, or economic impact.]

## 3. Functionality Affected
[List specific opcodes, domain entities, database tables, or tick loops modified by this feature.]

## 4. Agent Implementation Details
[List concrete Kotlin classes, algorithms, concurrency rules (e.g. GameDispatcher confinement), and interfaces to implement.]

## 5. Test Reference
- `org.opentibia.canary.<package>.<TestName>`: [Describe tests asserting this functionality.]

## 6. Canary Original Code Reference
- **Source Files**: `src/.../file.cpp`, `src/.../file.hpp` (or `data/.../script.lua`)
- **Canary Repository Root**: `../.projects/canary`
- **Key Methods / Opcodes**: [List original C++ functions or opcode numbers, e.g. `ProtocolGame::parseAttack (0xA1)`]
- **Original Canary Tests**: `tests/unit/.../test.cpp` (if applicable)
