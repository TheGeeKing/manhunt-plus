# 🧭 Manhunt+

**Manhunt+** is a powerful Minecraft manhunt plugin featuring compass tracking, exciting twists, and full control over the hunt!

---

## ⚙️ Features & Usage

### 🕹️ Commands

#### 🧍 Basic Setup

- `/speedrunner add|remove <player>` – Add/remove a speedrunner
- `/hunter add|remove <player>` – Add/remove a hunter
- `/manhunt start` – Start the manhunt
- `/manhunt stop` – Stop the manhunt
- `/manhunt list` – List all hunters and speedrunners
- `/compass <player>` – Get a tracking compass

#### 🧪 Extras

- `/manhunt prepare` – Wait for first speedrunner hit to start
- `/surround <speedrunner>` – Surround the speedrunner with all hunters
- `/randomvillage` – Teleport near the nearest random village type

#### ⚙️ Settings

- `/manhunt countdown <minutes>` – Set a max time limit (**0 to disable**)
- `/manhunt startcountdown <seconds>` – Freeze hunters at start (**0 to disable**)
- `/manhunt teamcompass <on|off>` – Toggle hunter team compass sidebar
- `/twist <twist>` – Apply a twist to the manhunt (default is `DEFAULT`)
- `/manhunt help` – Open the help menu

---

### 🌀 Twists

- `DEFAULT` – Standard manhunt rules
- `PIG_OP_LOOT` – Pigs drop OP loot (only for the speedrunner 🐷💎)
- **More twists coming soon! 🔧**

---

## 🛠️ Configuration

🗂️ Navigate to your server folder → `plugins/ManhuntPlus/config.yml`

- Calibration
  - `auto-calibration`: Enable/Disable auto compass calibration
  - `auto-calibration-interval` - Set the interval on which the compass updates. Recommended to increase this if used on larger servers or even completely disable it, restart server to apply changes
- Broadcasting
  - `broadcast-time`: true
  - `broadcast-time-every` - Broadcast time interval(in seconds)
- `surround-radius: DECIMAL` – Distance between speedrunner and hunters when using `/surround`. Default: `3`
- `name-tracking-compass`: Name the compass to which player its tracking

---

## 🚧 Coming Soon

- 🛠️ Custom Twist Tweaker
- 🎯 Lootpool Modifier

---

## 💬 Support, Tracking & More

- 💬 Join our [**Discord**](https://discord.tutla.net) for sneak peeks & community support
- ⭐ Star us on [**GitHub**](https://github.com/TutlaMC/manhunt-plus)
- 🌐 Visit our [**Website**](https://tutla.net) for updates

Think we’re missing something? **Contribute on [GitHub](https://github.com/TutlaMC/manhunt-plus)**

---

## 🧪 Other Tutla Studio Projects

- 🛡️ [**HardPlayers**](https://modrinth.com/mod/hardplayers) – Practice PvP with custom players
- 🧾 [**Cheat Recipes**](https://modrinth.com/datapack/cheat_recipes) – Instantly get any item with custom recipes
