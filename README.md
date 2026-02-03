# ScoreHub

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)

A simple and intuitive Android application for tracking game scores across multiple players.

## 📱 Features

- **Multi-game Support**: Currently supports Yahtzee (Yams), with more games planned
- **Player Management**: Create, edit, and manage player profiles with custom colors
- **Score Tracking**: Keep detailed records of all game sessions
- **Statistics**: View comprehensive statistics including:
  - Win/loss/draw percentages
  - Best and worst scores
  - Player rankings
  - Top 20 high scores
- **Multi-language**: Supports English and French
- **Offline First**: All data stored locally using Room database

## 🎮 Supported Games

### Yahtzee (Yams)
- Full scorecard implementation
- Upper section with bonus tracking
- Lower section with all standard categories
- Support for 1-4+ players
- Solo game mode (non-competitive)

## 📸 Screenshots

_Coming soon_

## 🛠️ Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM with Repository pattern
- **Database**: Room (SQLite)
- **UI**: Material Design Components
- **Async**: Kotlin Coroutines + Flow
- **Build System**: Gradle (Kotlin DSL)

## 📋 Requirements

- Android 7.0 (API 24) or higher
- ~10 MB storage space

## 🚀 Installation

### From F-Droid (Recommended)

_Coming soon_

### From Source

1. Clone the repository:
```bash
git clone https://github.com/trivialloop/scorehub.git
cd scorehub
```

2. Open the project in Android Studio

3. Build and run on your device or emulator

## 🏗️ Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test
./gradlew connectedAndroidTest
```

## 🧪 Testing

The project includes both unit tests and instrumented tests:

```bash
# Unit tests
./gradlew testDebugUnitTest

# Instrumented tests (requires connected device/emulator)
./gradlew connectedDebugAndroidTest
```

## 📖 Usage

1. **Select a Game**: Choose Yahtzee from the main menu
2. **Add Players**: Create or select existing players with unique colors
3. **Start Game**: Begin playing and enter scores as you go
4. **View Statistics**: Check player rankings and high scores from the menu

## 🗺️ Roadmap

- [ ] Add more games (Belote, Tarot, etc.)
- [ ] Export/import game data
- [ ] Dark theme support
- [ ] Game history browser
- [ ] Custom game modes

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the project
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

## 👤 Author

**TrivialLoop**

- GitHub: [@trivialloop](https://github.com/trivialloop)

## 🙏 Acknowledgments

- Material Design Icons
- Android Open Source Project
- F-Droid community

## 📞 Support

If you encounter any issues or have suggestions, please [open an issue](https://github.com/trivialloop/scorehub/issues).

---

Made with ❤️ for board game enthusiasts
