# VanillaRPG-Core

VanillaRPG-Core là bộ lõi RPG cho server Paper Minecraft, kết hợp gameplay vanilla với EliteMobs, dungeon, kinh tế người chơi, Texas Hold'em Poker và Elite Gacha.

Mục tiêu của pack là giữ nhịp chơi Minecraft quen thuộc nhưng tạo một endgame dài hơi: người chơi vẫn cần cày gear, Elite Coins, dungeon và trao đổi với nhau thay vì đạt Netherite rồi vượt qua mọi nội dung quá dễ dàng.

## Yêu cầu

- Paper `26.2`
- Java `25`
- EliteMobs `10.9.8` (cài vào `server-template/plugins/`)
- Client dùng resource pack của server để thấy bài Poker và vũ khí Majestica

## Tính năng

### EliteMobs và endgame

- Elite mob, boss, dungeon, class, quest và Elite Coin do EliteMobs quản lý.
- Gacha thay thế shop của Special Blacksmith: quay bằng Elite Coin hoặc Elite Gacha Ticket.
- Pool hiển thị tỉ lệ trực tiếp qua `/gacha odds`, có phần thưởng tiến trình, ticket và Soulbind Remover.
- Mười vũ khí Majestica cấp 100 là giải thưởng cực hiếm, khoảng `0.01%` mỗi món (`0.1%` tổng). Các món này soulbound.

### Poker

- Phòng Texas Hold'em riêng qua `/poker`.
- Có blind, pre-flop, flop, turn, river, raise/check/call/fold và boss bar cập nhật pot/lượt chơi.
- Khi vào phòng, inventory được tạm thay bằng bài và vật phẩm thao tác; đồ được hoàn lại khi rời phòng hoặc ngắt kết nối.

### Kinh tế và hoạt động

- Player market để mua bán giữa người chơi: `/market`. Mọi item không soulbound, gồm EliteMobs/custom item, đều có thể niêm yết.
- Shop vật phẩm cơ bản và bán khoáng sản: `/shop`.
- Profile, quest, achievement, title, token và dungeon do VanillaRPG-Core quản lý.
- Hiển thị máu mob hostile và damage nổi khi chiến đấu.

### Thế giới

- Template hỗ trợ TerraformGenerator, BetterStructures, Amplified Nether và Nullscape.
- Loot vanilla/BetterStructures của VanillaRPG mặc định tắt để tránh đè lên gear của EliteMobs.

## Cài đặt server mới

1. Tải source hoặc bản release và giải nén.
2. Mở `server-template/`, chấp nhận EULA trong `eula.txt` nếu cần.
3. Đặt `VanillaRPG-Core-<version>.jar` hiện hành trong `server-template/plugins/` và chỉ giữ một JAR VanillaRPG-Core.
4. Cài `EliteMobs.jar` phiên bản `10.9.8` vào `server-template/plugins/`.
5. Cấu hình URL resource pack trong `server-template/server.properties`:

   ```properties
   require-resource-pack=true
   resource-pack=https://your-host/BladesOfMajestica-PokerPack-v1.3.zip
   resource-pack-sha1=E319EDC017A45C2AD13512DA1E0F0124CE433191
   ```

6. Chạy `server-template/start.bat`.
7. Khi server đã khởi động, dùng `/em reload`, đợi EliteMobs nạp xong, rồi dùng `/rpg reload` nếu vừa chỉnh cấu hình gacha.

Resource pack gộp nằm ở `server-template/resourcepacks/BladesOfMajestica-PokerPack-v1.3.zip`. Hãy upload ZIP này lên một URL HTTPS tải trực tiếp trước khi đặt vào `server.properties`.

## Cập nhật

1. Tắt server và sao lưu `plugins/VanillaRPG-Core/rpg.db`.
2. Thay JAR cũ bằng một JAR VanillaRPG-Core mới duy nhất.
3. Nếu release đổi resource pack, upload pack mới, cập nhật URL + SHA-1, rồi khởi động lại server.
4. Không thay hoặc commit `rpg.db`, `ops.json`, `usercache.json` hay thư mục world. Đây là dữ liệu runtime của server.

## Lệnh chính

| Lệnh | Chức năng |
| --- | --- |
| `/profile` | Mở hồ sơ RPG |
| `/quests` | Xem nhiệm vụ |
| `/achievements` | Xem thành tựu |
| `/titles` | Chọn danh hiệu |
| `/dungeons` | Mở danh sách dungeon |
| `/market` | Mở chợ người chơi |
| `/shop` | Mở shop |
| `/gacha` | Mở Elite Gacha |
| `/gacha odds` | Xem pool và tỉ lệ quay |
| `/poker` | Vào phòng Poker |
| `/em` | Mở menu EliteMobs |
| `/rpg reload` | Reload cấu hình VanillaRPG (OP) |

## Cấu hình quan trọng

- `server-template/plugins/VanillaRPG-Core/gacha.yml`: giá, pool, tỉ lệ và milestone Gacha.
- `server-template/plugins/EliteMobs/customitems/`: custom item EliteMobs, gồm vũ khí Majestica.
- `server-template/plugins/EliteMobs/config.yml`: phải bật `useResourcePackEvenIfResourcePackManagerIsNotInstalled: true` để model Majestica hoạt động khi không dùng ResourcePackManager.
- `server-template/server.properties`: URL và SHA-1 resource pack.

## Phiên bản và release notes

README chỉ chứa hướng dẫn dùng bản hiện tại. Thông tin thay đổi theo từng phiên bản được phát hành ở [GitHub Releases](https://github.com/vietanh151103/VanillaRPG/releases) và [Git tags](https://github.com/vietanh151103/VanillaRPG/tags).
