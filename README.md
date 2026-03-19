# Realtime Messenger

Java desktop chat app with:

- JavaFX client built with `FXML + CSS + controllers`
- socket-based backend kept in `ChatServer` and `ChatProtocol`
- reusable UI components for sidebar items and message bubbles

## Structure

- `src/core/App.java`: bootstrap entry point for server/client
- `src/core/DesktopApp.java`: JavaFX application shell and scene navigation
- `src/controllers`: screen/component controllers
- `src/models`: client-side view models
- `src/services`: socket client service
- `src/views`: FXML views and reusable UI components
- `src/css`: shared stylesheet
- `src/core/ChatServer.java`, `src/core/ChatProtocol.java`, `src/core/UserStore.java`: backend and persistence
- `data/users.txt`: registered accounts

## Run In VS Code

Open the project root `chatbox` and run the `Run JavaFX` launch config.

If you press Run on `App.java` with no arguments:

- you get a JavaFX launcher screen
- you can start the server
- you can open the client

## Build From Terminal

```powershell
javac --module-path C:/javafx-sdk-21.0.10/lib --add-modules javafx.controls,javafx.fxml,javafx.swing -d bin (Get-ChildItem -Path src -Recurse -Filter *.java | Select-Object -ExpandProperty FullName)
```

## Run Server

```powershell
java -cp bin core.App server 5000
```

## Run Client

```powershell
java --module-path C:/javafx-sdk-21.0.10/lib --add-modules javafx.controls,javafx.fxml -cp bin core.App client 127.0.0.1 5000
```
```
java --module-path C:/javafx-sdk-21.0.10/lib --add-modules ALL-MODULE-PATH -cp bin core.App client 127.0.0.1 5000
```

## Clean 
```powershell
Get-ChildItem bin -Recurse -Filter *.class | Remove-Item -Force
```
## Notes

- `StoragePaths` keeps account data and avatars under `data/` reliably when launched from VS Code.
- The new JavaFX client does not depend on Swing.
- The old Swing files are still in the repo for compatibility, but the main entry point now launches the JavaFX client.
