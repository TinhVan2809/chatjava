## Java Realtime Chat Box

Ứng dụng chat real time bằng java thuần với:

- giao diện Swing cho auth và chat
- server socket broadcast tin nhắn theo thời gian thực
- UI JavaFX

## Cau truc thu muc

- `src`: mã nguồn Java
- `bin`: file `.class` sau khi build
- `data/users.txt`: file tài khoản dược tạo khi server chạy

## Build

```powershell || terminal
javac --module-path C:/javafx-sdk-21.0.10/lib --add-modules javafx.controls,javafx.swing -d bin src\*.java
```

## Run server

```powershell || terminal
cd chatbox
 java -cp bin App server 5000
```

## Run client

```powershell || terminal
cd chatbox
 java --module-path C:/javafx-sdk-21.0.10/lib --add-modules javafx.controls,javafx.swing -cp bin App client 127.0.0.1 5000
```

## Cấu hình 
- JDK v21
- JavaFX SDK v21