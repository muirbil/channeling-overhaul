# Channeling Overhaul

[Русская версия](README.ru.md)

Channeling stops being "lightning in a thunderstorm" and becomes a charge mechanic. A stuck trident gathers electricity from the sky, copper and lightning rods amplify it, and the trident releases it into the mob it hits as a chain discharge. Higher Channeling levels (up to XXXI with Apothic Enchanting) give a larger gathering radius, more capacity and more chain hops.

> **Unofficial addon for Apothic Enchanting, not affiliated with or endorsed by Shadows_of_Fire.**

Developed with the help of Claude Code (AI coding assistant).

<!-- TODO: hero GIF — a trident charging at a copper station, then a chain discharge through a group of mobs -->
> 🎞️ **[GIF placeholder: station charging + chain discharge]**

## Requirements

| Mod | Version |
| --- | --- |
| Minecraft | 26.1.2 (any 26.1.x from 26.1.2, below 26.2) |
| NeoForge | 26.1.2.64-beta or newer (built against 26.1.2.124) |
| Kotlin for Forge | 6.4 or newer (built against 6.4.0) |
| Apothic Enchanting | 2.1.0 or newer |
| Placebo | required by Apothic Enchanting; built against 10.0.0 |
| Apothic Attributes | required by Apothic Enchanting; built against 3.0.1 |

Apothic Attributes 3.0.0 crashes on NeoForge 26.1.2.70-beta and newer when a mob is hit, regardless of this mod. Use 3.0.1 or newer there.

Install the mod on both the server and the client.

## How it works

### Charge

- The charge lives in the trident item. The tooltip shows it as **Charge: X / Y**.
- Outside of charging (in the inventory, in hand, in flight) the charge slowly leaks, with a half-life of 150 seconds. You can charge up at a station and then go fight.
- Only tridents with Channeling hold a charge.

### Charging station

The main charging happens while the trident is **stuck in a block**. Throw it into the ground and it starts gathering charge within its radius R, once every 10 ticks, without leaking.

- **Sky.** Charge comes only from the sky, so the trident needs open sky above it. Without it nothing charges.

  | Weather | Rain biome, charge/s | Snow biome, charge/s |
  | --- | --- | --- |
  | Clear | 0.1 | 0.1 |
  | Rain / snow | 0.5 | 0.35 |
  | Thunderstorm | 1.5 | 1.0 |

  Biomes without precipitation (desert, savanna) always count as clear.
- **Copper** around the trident works as an antenna and multiplies the inflow. Closer blocks count more. Oxidation lowers conductivity: clean 1.0, exposed 0.75, weathered 0.5, oxidized 0.25. Waxed copper keeps its stage. All copper shapes count: blocks, cut, chiseled, grates, bulbs, doors, trapdoors and so on.
- **Lightning rods** add more: the closest one gives the most, each next one half as much.
- **Water source blocks** give a small bonus, at most +0.3.
- The total antenna multiplier is capped at **×3**.

### Lightning

Any lightning bolt striking within R of a stuck trident adds charge at once:

- **+50% of Qmax** if it strikes a lightning rod;
- **+25% of Qmax** anywhere else.

One bolt charges every stuck trident within range. This is the fastest way to fill high levels in a thunderstorm.

### Windup

While you hold right click with the trident, it tops up at half the inflow at your position (open sky required). Windup never raises the charge above **30% of Qmax**.

### Discharge

- The charge is spent only when a **thrown** trident **hits a mob** and holds at least 3 charge. The whole charge goes into the discharge.
- A miss into a block keeps the charge. So does a hit blocked by a shield or one that deals no damage.
- Melee attacks never spend the charge.
- Damage to the target: **D = 0.6 × Q × M**. M is the target's vulnerability:
  - +0.15 per piece of gold or copper armor;
  - +0.12 per piece of netherite;
  - +0.10 per piece of iron or chainmail;
  - +0.25 if the target is wet (in water or in rain).

  M is capped at 2. Leather, diamond and turtle shell give 0.
- A strong discharge (at least 50% of Qmax) strikes the target with a harmless visual lightning bolt and plays the trident's thunder horn. A weak one only crackles.
- The vanilla Channeling effect (lightning on hit in a thunderstorm) still works as before.

### Chain

After the primary target the discharge jumps to the nearest mob not yet hit:

- The reach is 4 blocks, or 6 to a conductive target (metal armor or wet).
- Each hop deals 0.6 of the previous one, multiplied by its own target's M.
- There is a short delay between hops, so you can watch the chain.
- Number of hops: min(8, ⌊L / 3⌋).

The chain never hits you, your tamed animals, your team allies, or players when PvP is off. With PvP on it hits other players; you can turn that off in the server config.

### Water

If the primary target stands in water, the discharge also hits every living entity in water within min(8, 3 + L / 5) blocks. The damage falls off with distance: 0.4 × 0.6 × Q × (1 − d / r) × M. Every entity is hit at most once per discharge, whether by the target hit, the water or the chain.

### Loyalty and keeping the trident

- **Loyalty in copper.** A Loyalty + Channeling trident that hits copper or a lightning rod stays stuck and charges, instead of returning. Pick it up by hand. It returns as usual from any other block, or when the block under it is broken.
- **Channeling tridents don't despawn.** A stuck trident with Channeling that you can pick up stays until you take it, so a station keeps working longer than a minute. Tridents thrown in creative mode or by mobs despawn as in vanilla.

### Visuals and sound

- Sparks fly from copper, rods and the sky to a charging trident.
- The trident sprite glows in four steps as it fills (in the inventory, on the ground, in item frames and on shelves; not yet in hand).
- A full trident chimes once.
- Chains draw a zigzag of sparks between targets.

<!-- TODO: screenshot — tooltip "Charge: X / Y" and the four glow steps of the trident sprite -->
> 🖼️ **[Screenshot placeholder: tooltip and glow steps]**

## Building a station

1. Pick a spot with **open sky**. Nothing above the trident, not even glass.
2. Lay **waxed copper** densely around the point where the trident will stick. Waxed copper doesn't oxidize, so it keeps its conductivity.
3. Put a **lightning rod** on a pillar to the side, so it doesn't cover the sky.
4. Optionally, dig a **water moat** around it.
5. Throw the trident into the middle and wait, best of all for a thunderstorm.

Example for Channeling V (R ≈ 6.5), top view:

```
  ~ ~ ~ ~ ~ ~ ~      #  waxed copper
  ~ # # # # # ~      I  lightning rod on a pillar (to the side, so it doesn't block the sky)
  ~ # # # # # ~      x  the trident is stuck here
  ~ # # x # # I      ~  water moat (small bonus)
  ~ # # # # # ~
  ~ # # # # # ~
  ~ ~ ~ ~ ~ ~ ~
```

These 24 copper blocks with the rod and the water reach the ×3 cap. In rain Channeling V fills almost completely in about a minute; in a thunderstorm one strike into the rod gives +20 charge at once. About 30 closely placed copper blocks reach the cap at any level, so huge copper fields don't help.

Use `/channeling station` next to a stuck trident to see what charges it.

<!-- TODO: screenshot — the example station above, built in game, with a trident in the middle -->
> 🖼️ **[Screenshot placeholder: the example station in game]**

## Levels

Defaults of the server config.

| Level | Capacity Qmax | Radius R, blocks | Chain hops | Damage at full charge, M = 1 | Water radius, blocks |
| --- | --- | --- | --- | --- | --- |
| I | 16 | 4.0 | 0 | 9.6 | 3.2 |
| III | 28 | 5.5 | 1 | 16.8 | 3.6 |
| V | 40 | 6.5 | 1 | 24 | 4.0 |
| X | 70 | 8.3 | 3 | 42 | 5.0 |
| XV | 100 | 9.7 | 5 | 60 | 6.0 |
| XX | 130 | 10.9 | 6 | 78 | 7.0 |
| XXV | 160 | 12.0 | 8 | 96 | 8.0 |
| XXXI | 196 | 13.1 | 8 | 117.6 | 8.0 |

Formulas: Qmax = 10 + 6·L, R = min(16, 2 + 2·√L), hops = min(8, ⌊L / 3⌋). For reference, a vanilla thrown trident deals 8 damage.

## Commands

All commands need operator permission (level 2) and are meant for testing stations and balance. The `charge` commands act on the trident with Channeling in your main hand.

| Command | What it does |
| --- | --- |
| `/channeling charge get` | Level, charge, Qmax and R of the trident, and whether the server config is loaded |
| `/channeling charge set <amount>` | Sets the charge, capped at Qmax |
| `/channeling charge fill` | Fills the trident to Qmax |
| `/channeling charge clear` | Removes the charge |
| `/channeling station` | For the closest stuck Channeling trident within 8 blocks: sky inflow S, antenna A with copper, rods and water, inflow I and charge |

## Configuration

### Server config

The file is `serverconfig/channeling_overhaul-server.toml` inside the world folder. Put a copy into `defaultconfigs/` to use it for new worlds. NeoForge syncs it to clients.

| Option | Default | Range | Meaning |
| --- | --- | --- | --- |
| `charge.capacityBase` | 10.0 | 0 – 10000 | Qmax(L) = capacityBase + capacityPerLevel · L |
| `charge.capacityPerLevel` | 6.0 | 0 – 1000 | |
| `charge.radiusBase` | 2.0 | 0 – 64 | R(L) = min(radiusMax, radiusBase + radiusPerSqrtLevel · √L), blocks |
| `charge.radiusPerSqrtLevel` | 2.0 | 0 – 64 | |
| `charge.radiusMax` | 16.0 | 1 – 32 | Hard cap of the radius |
| `charge.leakHalfLife` | 150.0 | 1 – 1000000 | Half-life of the charge in the inventory, in hand and in flight, seconds |
| `charge.chargeIntervalTicks` | 10 | 1 – 1200 | A stuck trident charges once per this many ticks |
| `charge.rescanInterval` | 5.0 | 0.5 – 600 | A stuck trident looks for copper, rods and water again after this many seconds |
| `sky.clear` | 0.1 | 0 – 100 | Sky inflow S in clear weather and in biomes without precipitation, charge/s |
| `sky.rain` | 0.5 | 0 – 100 | S in rain |
| `sky.snow` | 0.35 | 0 – 100 | S in snow |
| `sky.thunderRain` | 1.5 | 0 – 100 | S in a thunderstorm, rain biome |
| `sky.thunderSnow` | 1.0 | 0 – 100 | S in a thunderstorm, snow biome |
| `antenna.max` | 3.0 | 1 – 100 | Cap of the antenna multiplier A |
| `antenna.copper` | 0.1 | 0 – 10 | Weight of a copper block, times its conductivity and proximity |
| `antenna.rod` | 0.5 | 0 – 10 | Weight of the closest lightning rod, times proximity |
| `antenna.rodFalloff` | 0.5 | 0 – 1 | Each next rod gives this fraction of the previous one |
| `antenna.water` | 0.02 | 0 – 10 | Weight of a water source block, times proximity |
| `antenna.waterMax` | 0.3 | 0 – 10 | Cap of the water bonus |
| `lightning.rodBonus` | 0.5 | 0 – 1 | Lightning into a rod within R, fraction of Qmax |
| `lightning.nearbyBonus` | 0.25 | 0 – 1 | Lightning elsewhere within R, fraction of Qmax |
| `windup.factor` | 0.5 | 0 – 10 | Inflow at the player's position is multiplied by this |
| `windup.cap` | 0.3 | 0 – 1 | Windup never charges above this fraction of Qmax |
| `windup.rescanInterval` | 1.0 | 0.05 – 60 | A winding up player looks for sources at most this often, seconds |
| `discharge.damagePerCharge` | 0.6 | 0 – 100 | D = damagePerCharge · Q · M |
| `discharge.minCharge` | 3.0 | 0 – 10000 | Below this charge a hit does nothing and the charge stays |
| `discharge.vulnerabilityMax` | 2.0 | 1 – 100 | M = min(vulnerabilityMax, 1 + armor bonuses + wetBonus) |
| `discharge.wetBonus` | 0.25 | 0 – 10 | Bonus to M for a wet target |
| `discharge.strongDischarge` | 0.5 | 0 – 1 | Fraction of Qmax for the visual lightning and the horn |
| `discharge.overcharged` | 0.9 | 0 – 1 | Fraction of Qmax for the "vaporized" death message |
| `discharge.deathMessages` | `NAMED` | `VANILLA`, `NAMED`, `ALL` | Who else sees that a mob was killed by a discharge: nobody, everyone for named and tamed mobs, everyone for any mob. Respects the `show_death_messages` game rule |
| `sound.hornVolumeBase` | 1.0 | 0 – 100 | Horn volume = hornVolumeBase + hornVolumePerCharge · Q / Qmax (about 16 blocks of range per unit) |
| `sound.hornVolumePerCharge` | 4.0 | 0 – 100 | |
| `sound.crackleVolume` | 0.3 | 0 – 10 | Volume of the crackle of weak discharges and chain hops |
| `sound.cracklePitch` | 1.6 | 0.5 – 2 | Pitch of the crackle |
| `sound.distantThunder` | false | | An overcharged discharge of level `distantThunderMinLevel` and up also thunders for the whole dimension |
| `sound.distantThunderMinLevel` | 20 | 1 – 127 | |
| `chain.levelsPerJump` | 3 | 1 – 127 | Hops N(L) = min(maxJumps, ⌊L / levelsPerJump⌋) |
| `chain.maxJumps` | 8 | 0 – 64 | |
| `chain.range` | 4.0 | 0 – 32 | Search range for the next target, blocks |
| `chain.conductiveRange` | 6.0 | 0 – 32 | Search range for a conductive target (M > 1), blocks |
| `chain.falloff` | 0.6 | 0 – 1 | Each hop deals this fraction of the previous one, before the target's M |
| `chain.hopDelayTicks` | 2 | 1 – 100 | Ticks between hops |
| `chain.hitsPlayers` | true | | With PvP on, the chain and the water of a player's trident hit other players |
| `waterSpread.radiusBase` | 3.0 | 0 – 32 | r_w = min(radiusMax, radiusBase + radiusPerLevel · L) |
| `waterSpread.radiusPerLevel` | 0.2 | 0 – 32 | |
| `waterSpread.radiusMax` | 8.0 | 0 – 32 | |
| `waterSpread.factor` | 0.4 | 0 – 10 | D_water = factor · damagePerCharge · Q · (1 − d / r_w) · M |
| `waterSpread.sameWaterBody` | false | | Spread only through the water body of the primary target |
| `waterSpread.floodLimit` | 512 | 1 – 100000 | With `sameWaterBody`, at most this many water blocks are searched |
| `debug.dischargeChat` | false | | Tell the trident's owner Q, M and D of each discharge, hop and water hit, lightning bonuses and windups in chat |

### Client config

The file is `config/channeling_overhaul-client.toml`.

| Option | Default | Values | Meaning |
| --- | --- | --- | --- |
| `particles` | `ALL` | `ALL`, `REDUCED`, `MINIMAL` | How many of the mod's particles to draw: ×1, ×0.4, ×0.1. The vanilla "Particles" setting applies on top |

## Data packs

Everything below can be changed by a data pack without touching code.

### Conductors: `channeling_overhaul:conductivity`

A block data map at `data/channeling_overhaul/data_maps/block/conductivity.json`. Entries are blocks or block tags (`#`).

- `type` is `copper` (the default), `lightning_rod` or `water`.
- `conductivity` (0 – 10, default 1.0) is the conductivity c and only matters for copper.
- A `water` block counts only as a source block.

The mod's own entries use the block tags `channeling_overhaul:copper/unaffected`, `…/exposed`, `…/weathered` and `…/oxidized`, plus `#minecraft:lightning_rods` and `minecraft:water`. To make another mod's copper count, add it to one of these tags, or give it its own entry:

```json
{
  "values": {
    "examplemod:copper_wire": { "conductivity": 0.8 },
    "examplemod:iron_lightning_rod": { "type": "lightning_rod" },
    "#examplemod:brine": { "type": "water" }
  }
}
```

### Armor: `channeling_overhaul:armor_conductivity`

An item data map at `data/channeling_overhaul/data_maps/item/armor_conductivity.json`. `bonus` (0 – 10) is added to the target's M for each worn piece. All armor slots count, including the body slot of animals.

```json
{
  "values": {
    "examplemod:bronze_chestplate": { "bonus": 0.12 },
    "examplemod:rubber_boots": { "bonus": 0.0 }
  }
}
```

Data map files from different packs are merged. Use `"replace": true` to drop the entries of lower packs, or `"remove": [...]` to drop single entries.

### Immune mobs: `#channeling_overhaul:discharge_immune`

An entity type tag at `data/channeling_overhaul/tags/entity_type/discharge_immune.json`. The chain and the water never hit these mobs. It is empty by default.

```json
{
  "values": ["minecraft:iron_golem", "minecraft:snow_golem"]
}
```

### Enchantment levels: Apothic Enchanting's `enchantment_info`

The mod raises Channeling to level XXXI with an entry in `data/apothic_enchanting/data_maps/enchantment/enchantment_info.json`, and sets `max_level: 31` in `data/minecraft/enchantment/channeling.json`. Override the entry to change the levels or the power the enchanting table needs:

```json
{
  "values": {
    "minecraft:channeling": {
      "max_level": 31,
      "max_loot_level": 1,
      "level_cap": 31,
      "min_power": {
        "type": "expression",
        "expression": "IF(x <= 25, 4 * x, 100 + 15 * (x - 25))"
      }
    }
  }
}
```

## Compatibility

- **Lightning protection.** The three damage types of the mod — `channeling_overhaul:discharge`, `channeling_overhaul:discharge_chain` and `channeling_overhaul:discharge_water` — are in the tag `#minecraft:is_lightning`. Mods that protect from lightning by that tag protect from discharges too. The damage types are also in `#minecraft:bypasses_cooldown`, so the discharge lands in the same tick as the trident, and in `#minecraft:no_knockback`.
- **Attributes and crits.** Discharges are dealt as the trident owner's damage, so kills count for the player and armor reduces the damage. Apothic Attributes rolls critical strikes on discharges like on any damage from a living attacker. To turn that off, add the three damage types to `#apothic_attributes:cannot_critically_strike`. Apotheosis itself is optional and not tested yet.
- **Apothic Enchanting table.** Channeling goes up to XXXI. Up to XXV the power the table needs grows as 4·L, after that as 100 + 15·(L − 25). Rough guide: V — 20, X — 40, XX — 80, XXV — 100, XXX — 175 (maximum Eterna and a lucky Quanta), XXXI — 190, practically only on an anvil (XXX + XXX). Channeling from loot stays at level I.
- **Riptide** stays incompatible with Channeling, as in vanilla.

## Balance status

This is a **beta**. All numbers are starting values from the design document and have not been tuned in a playtest yet. Balance tuning is deferred until there is feedback from players.

Please report what feels too strong, too weak or broken in [Issues](../../issues). Mention your Channeling level, your station setup and the weather.

The full design, with all formulas and the reasoning behind them, is in [DESIGN.md](DESIGN.md) ([Russian translation](DESIGN.ru.md)).

## Building from source

You need JDK 25.

```bash
./gradlew build
```

The jar lands in `build/libs/`. Other useful tasks:

- `./gradlew test` — unit tests of the formulas and data files;
- `./gradlew runGameTestServer` — in-game tests (GameTest), not included in the release jar;
- `./gradlew runClient` — a dev client.

## License and credits

[MIT](LICENSE) © 2026 equi.

- [Shadows_of_Fire](https://github.com/Shadows-of-Fire) for Apothic Enchanting, Apothic Attributes and Placebo.
- [thedarkcolour](https://github.com/thedarkcolour) for Kotlin for Forge.
- The [NeoForged](https://neoforged.net/) team.

Developed with the help of Claude Code (AI coding assistant).
