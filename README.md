# Universal NPC Spawner - Forge 1.20.1

Standalone addon for Forge 1.20.1. It does not require Mine Piece at compile time; at runtime it detects the `minepiece` entity registry.

Features:
- controlled natural spawning of Mine Piece mob/character entities
- projectile/effect filtering
- later forms excluded from natural spawning
- rare East Blue/base Luffy/Zoro/Sanji spawning with biome restrictions
- configurable spawn rate/radius/count in `config/universalspawner-common.toml`
- Development Stone item for supported form chains
- keeps UUID and persistent NBT when evolving, preserving Universal Crew ownership/state

Evolution chains included:
- Luffy: `luffy_east_blue -> luffy_1 -> luffy_2_years_later -> luffy_wano_country`
- Zoro: `zoro_east_blue -> zoro_1 -> zoro_2_years_later -> zoro_onigashima`
- Sanji: `sanji_east_blue -> sanji_1 -> sanji_2_years_later -> sanji_onigashima`
- Law: Grand Line -> Seven Warlords -> Wano when those registry ids are present.

Install the built jar into the Forge 1.20.1 `mods` folder alongside Mine Piece.


Final repair: Forge 1.20.1 API imports cleaned up; development-stone evolution also updates the visible form name.
