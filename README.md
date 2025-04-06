# Python Function Tracer Plugin for PyCharm

A PyCharm plugin that provides real-time function execution time profiling using Python's tracing capabilities and Debug Adapter Protocol (DAP) integration. <br>
This plugin allows developers to trace specific functions during debugging sessions without modifying source code.

## Building from Source

This project uses Gradle for building and testing.

### Prerequisites

- JDK 17 or newer
- Gradle (as the project includes a Gradle wrapper)

### Build Commands

```bash
# Clone the repository
git clone git@github.com:JanaDragovic/pycharm-debug-plugin.git
cd function-tracer-plugin

# Build the plugin
./gradlew build

# Run the plugin in a development instance of PyCharm
./gradlew runIde

```

### Project Structure

```
function-tracer-plugin/
├── build.gradle.kts              # Gradle build configuration
├── src/
    ├── main/
        ├── kotlin/               # Kotlin source files
        │   └── com/
        │       └── jana/
        │           └── functiontracer/
        │               ├── FunctionTracerPlugin.kt
        │               ├── FunctionTracerProjectService.kt
        │               ├── actions/
        │               ├── debug/
        │               ├── model/
        │               ├── service/
        │               └── ui/
        └── resources/           # Resource files
            ├── META-INF/
            │   └── plugin.xml   # Plugin configuration
            ├── icons/
            │   └── tracer.svg   # Plugin icon
            └── python/
                └── tracer.py    # Python tracing code
```
