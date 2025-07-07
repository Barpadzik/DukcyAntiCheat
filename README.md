# 🦆 DuckyAC

**DuckyAC** is a lightweight, modular, and fully configurable anti-cheat plugin for Spigot/Paper Minecraft servers.  
It detects unnatural player behavior in block placing, movement, and other interactions to effectively prevent cheating.

> ✅ **Recommended**: Use **DuckyAC** in combination with **[Vulcan](https://www.spigotmc.org/resources/vulcan-anti-cheat-advanced-cheat-detection-1-8-1-21-5.83626/)** and **[GrimAC](https://modrinth.com/plugin/lightning-grim-anticheat)** for maximum cheat detection coverage.

---

## ⚙️ Features

- 🔍 **Checks List**:
  - `AutoTotemA`: Detects suspicious totem planting (beta check)
  - `ElytraAimA`: Player was detected to be hitting too fast while flying an elytra
  - `ElytraCriticalsA`: Detects when a player deals too much critical damage while flying an elytra in too short a time
  - `FastClimbA`: Detects to fast player climbing on ladder, vines etc.
  - `FastPlaceA`: Detects when a player places too many blocks at a time
  - `InvalidPlaceA`: Detects when a player has placed a block at the wrong angle
  - `NoSlowDownA-G`: Many features of the player walking too fast during certain activities
  - `NoWebA`: Detects player movement that is too fast while in a web
  - `ThruBlocksA`: Detects when a player hits another player through a wall
  - `TimerA-D`: Detects when a player sends too many packets
- 📉 Violation Level (VL) system for tracking repeated offenses
- 🔧 Fully configurable thresholds, punishments, and enabled checks
- 🛡 Permission-based bypass support (e.g., for admins)
- 🔔 Alerts in chat and console to authorized staff (`duckyac.alerts`)
- 📩 Discord webhook support for sending logs and punishments
- 🧠 Config setting caching for performance optimization
- 🔄 Optional command to reload config (`/duckyac reload`)

---

## 🧪 Sample Configuration (`config.yml`)

```yaml
# === DUCKY ANTICHEAT CONFIGURATION ===

# Time after which violations reset for the player
# How it works:
# Player is reported let's say 2 times for check ElytraAimA
# After the default 300 seconds or 5 minutes his violations will reset to 0

alert-timeout: 300

# === AUTO TOTEM A ===
auto-totem-a:
  enabled: true
  min-delay: 150 # minimum allowed swap interval in ms
  tick-interval: 3
  max-ping: -1
  max-alerts: 5
  command: "kick %player% Too fast totem swap (AutoTotemA)"
  debug: false

# === ELYTRA AIM A ===
elytra-aim-a:
  enabled: true
  cancel-event: true
  max-firework-delay: 200
  max-alerts: 5
  command: "kick %player% Cheating with Elytra (ElytraAimA)"
  debug: false

# === ELYTRA CRITICALS A ===
elytra-criticals-a:
  enabled: true
  cancel-event: true
  critical-hits-required: 3
  timeframe: 2000 # in milliseconds
  max-alerts: 5
  command: "kick %player% Cheating with Elytra (ElytraCriticalsA)"
  debug: false

# === THRU BLOCKS A ===
thru-blocks-a:
  enabled: true
  cancel-event: true
  max-distance: 1.0 # Max allowed distance from clear line of sight
  max-alerts: 4
  command: "kick %player% Hitting through blocks (ThruBlocksA)"
  debug: false

# === NO WEB A ===
no-web-a:
  enabled: true
  cancel-event: true
  min-web-vertical-slow: 0.25
  min-web-horizontal-slow: 0.1
  jump-violation-threshold: 0.42
  max-alerts: 3
  command: "kick %player% Suspicious movement in cobwebs (NoWebA)"
  debug: false

# === NO SLOW ALL CHECKS ===
no-slowdown-max-distance: 1.0

# === NO SLOW A ===
no-slowdown-a:
  enabled: true
  cancel-event: false
  max-eating-speed: 0.20
  ignored-speeds:
    - 0.5072
    - 0.3024
    - 0.2933
    - 0.4822
    - 0.5013
    - 0.5014
  max-alerts: 10
  command: "kick %player% Player was walking too fast while eating (NoSlowDownA)"
  debug: false

# === NO SLOW B ===
no-slowdown-b:
  enabled: true
  cancel-event: false
  max-bow-speed: 0.20
  ignored-speeds:
    - 0.5072
    - 0.3024
    - 0.2933
    - 0.4822
    - 0.5013
    - 0.5014
  max-alerts: 10
  command: "kick %player% Player was walking too fast with a drawn bow (NoSlowDownB)"
  debug: false

# === NO SLOW C ===
no-slowdown-c:
  enabled: true
  cancel-event: false
  max-speed: 0.20
  ignored-speeds:
    - 0.5072
    - 0.3024
    - 0.2933
    - 0.4822
    - 0.5013
    - 0.5014
  max-alerts: 10
  command: "kick %player% Player walked too fast while drawing the crossbow (NoSlowDownC)"
  debug: false

# === NO SLOW D ===
no-slowdown-d:
  enabled: true
  cancel-event: false
  max-speed: 0.20
  ignored-speeds:
    - 0.5072
    - 0.3024
    - 0.2933
    - 0.4822
    - 0.5013
    - 0.5014
  max-alerts: 10
  command: "kick %player% Player was walking too fast while holding a shield (NoSlowDownD)"
  debug: false

# === NO SLOW E ===
no-slowdown-e:
  enabled: true
  max-speed: 0.170
  max-alerts: 5
  command: "kick %player% Player was walking too fast on honey block (NoSlowDownE)"
  debug: false

# === NO SLOW F ===
no-slowdown-f:
  enabled: true
  max-speed: 0.170
  max-alerts: 5
  command: "kick %player% Player was walking too fast on soul sand (NoSlowDownF)"
  debug: false

# === NO SLOW G ===
no-slowdown-g:
  enabled: true
  max-speed: 0.135
  max-alerts: 10
  command: "kick %player% Player was walking too fast while sneaking (NoSlowDownG)"
  debug: false

# === INVALID PLACE A ===
invalid-place-a:
  enabled: true
  cancel-event: true
  max-angle: 50 # Maximum angle allowed between a looking direction and placed block
  max-alerts: 3
  command: "kick %player% Invalid block placement (InvalidPlaceA)"
  debug: false

# === FAST PLACE A ===
fast-place:
  enabled: true
  cancel-event: true
  max-per-tick: 4
  max-alerts: 3
  command: "kick %player% Too fast block placement (FastPlaceA)"
  debug: false

# === TIMER A === // Beta Check !
timer-a:
  enabled: true
  cancel-event: true
  max-packets-per-second: 24
  max-alerts: 10
  command: "kick %player% You send too many packets (TimerA)"
  debug: false # Danger! This may cause lags with bigger amount of players!

# === TIMER B === // Beta Check !
timer-b:
  enabled: true
  cancel-event: true
  max-packets-per-second: 24
  max-alerts: 10
  command: "kick %player% You send too many packets (TimerB)"
  debug: false # Danger! This may cause lags with bigger amount of players!

# === TIMER C === // Beta Check !
timer-c:
  enabled: true
  cancel-event: true
  max-packets-per-second: 24
  max-alerts: 10
  command: "kick %player% You send too many packets (TimerC)"
  debug: false # Danger! This may cause lags with bigger amount of players!

# === DUCKY ANTI CHEAT MESSAGES ===

# === VIOLATION ALERTS ===
alert-message: "&d&lDuckyAC &8» &fPlayer &7»&f %player% &7»&6 %check% &7(&c%vl%VL&7)"

# === DISCORD TEMPLATES ===
discord:
  enabled: false
  discord-webhook-url: "https://discord.com/api/webhooks/your-webhook-id"
  username: "DuckyAntiCheat"
  avatar-url: "https://i.imgur.com/ahbEPVO.png"
  violation-message-template: "**AntiCheatSystem**\nPlayer: **%player%**\nCheck: **%check%**\nViolation: **%vl%**"
  punishment-message-template: "**Punishment Executed**\nPlayer: **%player%**\nCommand: `%command%`"

# === MISC ===
no-permission: "&d&lDuckyAC &8» &cNo Permission!"
incorrect-usage: '&d&lDuckyAC &8» &cUsage: /duckyac reload'
update-available: "&d&lDuckyAC &8» &eA new version is available: &c%version%"
update-download: "&d&lDuckyAC &8» &eDownload: &a%url%"
update-check-failed: "&d&lDuckyAC &8» &cCould not check for updates."
player-only: "&d&lDuckyAC &8» &cOnly Players can use this command."
config-reloaded: '&d&lDuckyAC &8» &aConfiguration reloaded.'
plugin-reloaded: '&d&lDuckyAC &8» &aPlugin successfully reloaded.'
```

---

## 🔐 Permissions

| Permission | Description |
|------------|-------------|
| `duckyac.bypass` | Completely disables checks for the player |
| `duckyac.bypass.<checkname>-<check-letter example- a>` | Disables only defined check for the player |
| `duckyac.*` | Full access (bypass + admin permissions) |
| `duckyac.alerts` | Receive alert messages in chat |
| `duckyac.update` | Receive messages in chat about an available update |

---

## 🚀 Installation

1. Place `DuckyAC.jar` in your server’s `plugins/` folder.
2. Start your server.
3. Configure the plugin in `plugins/DuckyAC/config.yml`.
4. Reload plugin with `/duckyac reload`.
5. Done!

---

## 🛠 Planned Features

- Add more combat checks!

---

## 🤝 Contributing / Support

Found a bug or have a suggestion?  
Reach out via Discord or open an issue in the GitHub repository!

---

## 📜 License

DuckyAC is licensed under the GPL-3.0 License.  
You are free to use, modify, and redistribute it under the terms of the license.

---

## 💡 Recommendation

For the best protection against modern cheat clients, it is **strongly recommended** to run **DuckyAC alongside [Vulcan](https://www.spigotmc.org/resources/vulcan-anti-cheat-advanced-cheat-detection-1-8-1-21-5.83626/)** and **[GrimAC](https://modrinth.com/plugin/lightning-grim-anticheat)**.  
Each plugin covers different types of exploits, and together they form a powerful defense.

---
