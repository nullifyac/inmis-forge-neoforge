# Inmis - Multi-Version Modding Repository

![Minecraft](https://img.shields.io/badge/Minecraft-Java%20Edition-brightgreen)
![License](https://img.shields.io/github/license/Draylar/inmis)
![Stars](https://img.shields.io/github/stars/Draylar/inmis)

**Inmis** is a feature-rich Backpack mod for Minecraft, available across multiple Minecraft versions and modding platforms. This repository contains optimized implementations for **Forge** (1.18.2, 1.19.2, 1.20.1) and **NeoForge** (1.21.1).

## 📦 Overview

Inmis provides players with customizable backpacks that can be:
- Opened via right-click or keybind (default: **B**)
- Equipped in the Chestplate armor slot
- Enhanced with Trinket compatibility for additional equip slots
- Configured through `config/inmis.json` for custom gameplay

## 📂 Repository Structure

```
inmis-forge-neoforge/
├── forge-1.18.2/          # Minecraft 1.18.2 (Forge)
│   ├── inmis/             # Main mod source
│   ├── Curios-1.18.x/     # Curios mod (dependency)
│   └── release/           # Built artifacts
├── forge-1.19.2/          # Minecraft 1.19.2 (Forge)
│   ├── inmis/             # Main mod source
│   ├── Curios-1.19.x/     # Curios mod (dependency)
│   └── release/           # Built artifacts
├── forge-1.20.1/          # Minecraft 1.20.1 (Forge)
│   ├── inmis/             # Main mod source
│   ├── Curios-1.20.x/     # Curios mod (dependency)
│   └── release/           # Built artifacts
├── neoforge-1.21.1/       # Minecraft 1.21.1 (NeoForge)
│   ├── inmis/             # Main mod source
│   ├── Curios-1.21.x/     # Curios mod (dependency)
│   └── release/           # Built artifacts
└── images/                # Documentation images
```

## 🚀 Quick Start

### Prerequisites
- Java Development Kit (JDK) 8+ for older versions, JDK 17+ for 1.21.1
- Gradle (included via gradlew)

### Building a Version

Navigate to the desired version directory and run:

```bash
cd forge-1.20.1/inmis
./gradlew build
```

The compiled mod will be available in `build/libs/`.

## 📋 Features

### Core Functionality
- **Multiple Backpack Types**: Frayed, Baby, Plated, Gilded, Bejeweled, Withered, and Endless Backpacks
- **Inventory Management**: 27-slot storage in most backpacks
- **Quick Access**: Default keybind **B** to open the first backpack in inventory
- **Armor Integration**: Equip backpacks in the chestplate slot

### Compatibility
- **Trinkets Mod**: Equip backpacks in dedicated Trinket slots for enhanced gameplay
- **ShulkerBoxTooltip**: Preview backpack contents via tooltip

### Configuration
Customize gameplay through `config/inmis.json`:
- Disable main-inventory backpacks
- Require players to wear backpacks in armor slots
- Add custom backpacks with custom properties
- Adjust inventory sizes and features

## 📁 Version-Specific Details

### Forge Versions (1.18.2, 1.19.2, 1.20.1)
Each Forge version includes:
- Optimized Forge API integration
- Tested compatibility with popular Forge mods
- Curios mod support for enhanced trinket slots

### NeoForge (1.21.1)
Latest version with:
- Modern NeoForge API
- Updated to Minecraft 1.21.1 features
- Enhanced performance optimizations

## 🔧 Development

### Project Structure
Each version follows the standard Minecraft mod development structure:
```
inmis/
├── src/
│   ├── main/
│   │   ├── java/          # Source code
│   │   └── resources/     # Assets and configs
│   └── test/              # Unit tests
├── assets/                # Textures and UI assets
├── build.gradle           # Build configuration
├── gradle.properties      # Gradle properties
└── settings.gradle        # Gradle settings
```

### Building from Source
1. Clone the repository
2. Navigate to your desired version
3. Run `./gradlew build`
4. Find the built JAR in `build/libs/`

### Dependencies
- **Forge/NeoForge**: Modding framework
- **Curios**: Trinket/accessory system
- **Fabric API** (if using compatibility layer): Utility functions

## 📝 License

Inmis is licensed under the **MIT License**. See [LICENSE](LICENSE) for details.

## 🤝 Contributing

Contributions are welcome! Please consider:
1. Opening an issue to discuss major changes
2. Testing across all supported versions
3. Following the existing code style
4. Documenting new features in config examples

## 📞 Support & Contact

For issues, questions, or suggestions:
- Check the [GitHub Issues](https://github.com/Draylar/inmis/issues)
- Review configuration examples in each version's `config/inmis.json`

## 🎮 Installation

### For Players
1. Download the appropriate JAR for your Minecraft version from releases
2. Place it in your mods folder
3. Install [Curios](https://www.curseforge.com/minecraft/mc-mods/curios) (required for armor equipping)
4. Optionally install [Trinkets](https://www.curseforge.com/minecraft/mc-mods/trinkets-fabric) for Trinket compatibility

### For Developers
See the Development section above for building from source.

## 🔄 Version Support Timeline

| Version | Modloader | Status | Java |
|---------|-----------|--------|------|
| 1.18.2  | Forge     | Active | 8+   |
| 1.19.2  | Forge     | Active | 17+  |
| 1.20.1  | Forge     | Active | 17+  |
| 1.21.1  | NeoForge  | Active | 21+  |

## 📊 Project Statistics

- **Multiple Version Support**: 4 major Minecraft versions
- **Active Development**: Continuously updated
- **Community-Focused**: Feedback-driven improvements
- **Stable & Tested**: Thoroughly tested across platforms

---

**Last Updated**: January 2026

For the latest updates, visit the [GitHub repository](https://github.com/Draylar/inmis).
