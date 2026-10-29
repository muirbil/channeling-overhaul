# Changelog

All notable changes to this project are documented here. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/).

## [0.1.0-beta] — 2026-10-29

First public release, for Minecraft 26.1.2, NeoForge 26.1.2.64-beta+, Kotlin for Forge 6.4+ and Apothic Enchanting 2.1.0+.

### Added

- **Charge in the trident.** Channeling tridents store charge in the item, shown in the tooltip, with capacity Qmax = 10 + 6·L. Outside of charging it leaks with a half-life of 150 s.
- **Charging while stuck.** A stuck trident charges from the sky under open sky. The inflow depends on the weather and the biome's precipitation. The gathering radius is R = 2 + 2·√L, capped at 16.
- **Antenna.** Copper by oxidation stage, lightning rods and water source blocks multiply the inflow, up to ×3. They are defined by the block data map `channeling_overhaul:conductivity`.
- **Lightning.** A bolt within R of a stuck trident adds +50% Qmax when it strikes a lightning rod and +25% elsewhere.
- **Windup.** Holding right click tops the trident up at half the inflow, up to 30% Qmax.
- **Discharge.** A thrown trident hitting a mob with at least 3 charge spends it all: D = 0.6·Q·M.
  - The target's vulnerability M comes from armor (item data map `channeling_overhaul:armor_conductivity`) and from being wet, capped at 2.
  - A miss, a shield block or a refused hit keeps the charge. Melee never spends it.
- **Chain.** Up to min(8, ⌊L / 3⌋) hops, each 0.6 of the previous one, 2 ticks apart.
  - The reach is 4 blocks, or 6 to conductive targets.
  - The chain skips the owner, their pets, team allies, players with PvP off and the entity tag `channeling_overhaul:discharge_immune`.
- **Water spread.** A primary target in water spreads the discharge to everyone in water within min(8, 3 + L / 5) blocks. An optional same-water-body mode is in the config.
- **Damage types.** `channeling_overhaul:discharge`, `discharge_chain` and `discharge_water` are in `#minecraft:is_lightning`, with their own death messages in English and Russian. A config option shows mob deaths from discharges to all players.
- **Loyalty in copper.** A Loyalty + Channeling trident stays stuck in copper or a lightning rod to charge.
- **No despawn.** Stuck Channeling tridents that can be picked up don't despawn.
- **Enchantment levels.** Channeling goes up to level XXXI through Apothic Enchanting, with an enchanting table power curve for high levels.
- **Visuals and sound:**
  - gathering sparks and a full-charge chime;
  - four glow steps on the trident sprite;
  - spark arcs along the chain and flashes on water hits;
  - a harmless visual lightning bolt and a thunder horn for strong discharges;
  - an optional distant thunder for overcharged discharges at level XX and above.
- **Server config** with all balance numbers and switches. **Client config** for particle density.
- **Operator commands:** `/channeling charge get | set <amount> | fill | clear` and `/channeling station`.

### Known limitations

- Balance numbers are starting values and have not been playtested. Tuning waits for player feedback.
- The glow shows only on the flat trident sprite, not on the 3D model in hand.
- Apotheosis integration is not tested yet.

[0.1.0-beta]: ../../releases/tag/v0.1.0-beta
