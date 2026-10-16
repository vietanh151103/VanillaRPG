# VanillaRPG

VanillaRPG là bộ server Paper 26.2 kết hợp Minecraft vanilla, EliteMobs, Elite Gacha, chợ người chơi, shop, quest, achievement, title và Texas Hold’em Poker.

README này hướng dẫn cài một server mới từ release hiện tại. Dữ liệu người chơi, database và world runtime không nằm trong release sạch.

## Yêu cầu

- Paper Minecraft `26.2` (template tự tải đúng build khi khởi động lần đầu).
- Java `25`.
- EliteMobs `10.9.8`, đặt trong `server-template/plugins/`.
- Windows: dùng `start.bat`; Linux/macOS: dùng `start.sh`.
- Internet trong lần chạy đầu để tải Paper và để ResourcePackManager host resource pack.

## Cài server mới

### 1. Tải release

Tải source ZIP hoặc server package từ [GitHub Releases](https://github.com/vietanh151103/VanillaRPG/releases), rồi giải nén vào một thư mục riêng.

Nếu tải source ZIP, thư mục chạy server là `server-template/`.

### 2. Chuẩn bị Java

Kiểm tra Java:

```text
java -version
```

Phải hiển thị Java 25. Nếu không muốn cài Java hệ thống, đặt Java portable tại:

```text
server-template/runtime/bin/java.exe
```

### 3. Cài EliteMobs

Đặt file plugin vào:

```text
server-template/plugins/EliteMobs.jar
```

Không đặt nhiều bản EliteMobs cùng lúc. VanillaRPG-Core cần EliteMobs để dùng Elite Coins, item EliteMobs và gacha.

### 4. Chấp nhận EULA

Mở `server-template/eula.txt` và đổi:

```text
eula=true
```

### 5. Khởi động server

Windows:

```text
cd server-template
start.bat
```

Linux/macOS:

```text
cd server-template
chmod +x start.sh
./start.sh
```

Lần chạy đầu sẽ tạo world mới và tải Paper nếu `paper.jar` chưa có. Sau khi server khởi động, cấp quyền quản trị trong console:

```text
op <ten-nguoi-choi>
whitelist add <ten-nguoi-choi>
```

## Resource pack

ResourcePackManager tự gộp và host resource pack. Không cần tự đặt URL pack trong `server.properties`.

Sau khi thấy log:

```text
Resource pack is live via automatic hosting
```

hãy vào server bằng Minecraft. Nếu vừa thay model hoặc resource pack, dùng:

```text
/rspm reload
```

Sau đó chờ log báo pack sẵn sàng rồi rejoin nếu client chưa nhận pack. Nguồn pack nằm tại:

```text
server-template/plugins/ResourcePackManager/resource_pack/
```

## Lệnh chính

| Lệnh | Chức năng |
| --- | --- |
| `/profile` | Mở hồ sơ RPG |
| `/quests` | Xem daily/weekly quest |
| `/achievements` | Xem achievement |
| `/titles` | Chọn title đã mở khóa |
| `/market` | Chợ mua bán giữa người chơi |
| `/shop` | Shop vật phẩm và bán item |
| `/gacha` | Mở Elite Gacha |
| `/gacha odds` | Xem pool và tỷ lệ gacha |
| `/poker` | Vào phòng Texas Hold’em |
| `/em` hoặc `/ag` | Menu/Adventurer’s Guild của EliteMobs |
| `/rpg reload` | Reload cấu hình VanillaRPG-Core (OP) |

Lệnh quản trị title:

```text
/rpg title give <player> <titleId>
/rpg title create <id> <name> <color>
```

Ví dụ:

```text
/rpg title give vanh owner
/rpg title create champion Champion GOLD
/rpg title create guild_master "Guild Master" AQUA
```

Các lệnh `/rpg` quản trị yêu cầu quyền `vanillarpg.admin`.

## Cập nhật server đang chạy

1. Dừng server bằng `stop` và chờ tắt hoàn toàn.
2. Sao lưu toàn bộ thư mục server, đặc biệt:
   ```text
   plugins/VanillaRPG-Core/rpg.db
   plugins/EliteMobs/data/player_data.db
   world/
   ```
3. Thay JAR VanillaRPG-Core bằng bản mới và chỉ giữ một JAR VanillaRPG-Core trong `plugins/`.
4. Không xoá `rpg.db`, `player_data.db`, world hoặc thư mục `playerdata` nếu muốn giữ tiến trình người chơi.
5. Khởi động lại server. Dùng `/rpg reload` cho thay đổi YAML; thay đổi Java/plugin cần restart.

## Các file cấu hình quan trọng

- `plugins/VanillaRPG-Core/config.yml`: economy, shop, market, poker và các module RPG.
- `plugins/VanillaRPG-Core/gacha.yml`: giá roll, pool, tỷ lệ và milestone gacha.
- `plugins/VanillaRPG-Core/quests.yml`: daily/weekly quest, achievement và title.
- `plugins/EliteMobs/customitems/`: custom item EliteMobs.
- `plugins/EliteMobs/data/player_data.db`: dữ liệu người chơi EliteMobs; không đưa lên Git.
- `plugins/ResourcePackManager/resource_pack/`: nguồn resource pack được gộp và host.

VanillaRPG legacy dungeon đang tắt trong config; dungeon EliteMobs vẫn hoạt động độc lập nếu nội dung EliteMobs được cài.

## Sao lưu và reset dữ liệu

Để chuyển server sang máy khác, dừng server rồi copy toàn bộ thư mục server. Giữ nguyên `world/`, `plugins/VanillaRPG-Core/rpg.db` và `plugins/EliteMobs/data/player_data.db`.

Để reset toàn bộ tiến trình EliteMobs, dừng server và sao lưu trước, sau đó xoá `plugins/EliteMobs/data/player_data.db` cùng các file `-wal`/`-shm` nếu có. Thao tác này reset tất cả người chơi, không chỉ một người.

## Build plugin từ source

Yêu cầu Gradle và Java 25. Từ thư mục project:

```text
cd plugin
gradle clean build
```

JAR được tạo tại `plugin/build/libs/`. Khi triển khai, copy JAR mới vào `server-template/plugins/` và xoá JAR phiên bản cũ.

## Release

README chỉ mô tả cách cài bản hiện tại. Lịch sử thay đổi và file tải xuống nằm tại [GitHub Releases](https://github.com/vietanh151103/VanillaRPG/releases).
