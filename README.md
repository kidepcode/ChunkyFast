# ChunkyFast

ChunkyFast is a high-performance chunk pregenerator for **Paper 1.21.x**. It provides a clean, predictable workflow for pregenerating terrain, supports running **multiple world jobs in parallel**, and includes built-in scheduling and load control.

---

## Key Features

- **Async chunk generation** using Paper’s async chunk API
- **Multi-world pregeneration**: run jobs for multiple worlds at the same time
- **Global scheduler**: shared budgets across all active jobs so load does not scale linearly with the number of worlds
- **MSPT-based limiter**: dynamically adjusts throughput to keep average tick time near your configured target
- **Region-friendly traversal**: chunk iteration optimized for better region (.mca) locality
- **Optional skipping of already-generated chunks** using region header scanning (fast, non-blocking)
- **Auto-save + auto-resume**: job state is persisted and can resume after restart
- **MiniMessage** formatting for all plugin messages

---

## Supported Versions

- **Paper**: 1.21.x  
- **Java**: 21+  

---

## Installation

1. Download the latest release from **GitHub Releases**.
2. Put `ChunkyFast.jar` into your server `plugins/` folder.
3. Start the server once to generate the default config.
4. Adjust `plugins/ChunkyFast/config.yml` if needed.
5. Use the commands below.

---

## Commands

Main command: `/chunkyfast`  
Aliases: `/cf`, `/cfast`

### Start pregeneration

All coordinates and radii are in **CHUNKS** (not blocks).

```text
/chunkyfast start <world> circle <radiusChunks> [centerX centerZ]
/chunkyfast start <world> square <radiusChunks> [centerX centerZ]
/chunkyfast start <world> rect <minX> <minZ> <maxX> <maxZ>
/chunkyfast start <world> worldborder
