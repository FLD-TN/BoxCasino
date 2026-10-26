# BoxCasino

Sòng bạc của server BoxPvP. Cược bằng **Tiền (Vault)** hoặc **Gem (BoxCore)**, không dùng tiền thật.
Spec đầy đủ: [BOXCASINO_SPEC.md](BOXCASINO_SPEC.md). Ghi chú về plugin Vegas: [VEGAS_NOTES.md](VEGAS_NOTES.md).

Bản hiện tại: **0.7.3** - Dò Mìn (`mines`), Kim Tự Tháp (`plinko`), Leo Thang (`stairs`), Vòng Quay (`roulette`),
Đua Ngựa chung server (`races`), Xì Dách 3 người vs nhà cái (`xidach`), và PvP 1v1: Xúc Xắc (`dice`),
Oẳn Tù Tì (`rps`), Cờ Caro (`tictactoe`). Có Hũ Jackpot, bảng xếp hạng, thông báo thắng lớn và máy chơi đặt trong world.

## Cài đặt

1. Cần có: Paper 1.21.1, **Vault** + plugin kinh tế, **BoxCore ≥ 1.3.0**. Nên có: WorldGuard, ItemsAdder, PlaceholderAPI.
2. Pack: dùng **pack đã sửa trong `BoxCasino/pack/`** (chữ CASINO / NHÀ CÁI), không dùng pack gốc của Vegas.
   - Chép `pack/iavegasgui` (GUI), `pack/iavegastables` (máy chơi) và `pack/boxcasino` (nút trò chuyện) vào
     `plugins/ItemsAdder/contents/`.
   - Nếu đã cài `iavegasgui` gốc trước đó: xoá thư mục cũ rồi chép thư mục mới vào.
   - Chạy `/ia zip`, thoát game vào lại để nhận pack.
   (Không cần cài plugin Vegas.)
3. Chép `build/libs/BoxCasino-0.7.3.jar` vào `plugins/` (xoá jar cũ), khởi động server.
   Nâng cấp từ bản cũ: file config cũ được chuyển vào `plugins/BoxCasino/old-configs/` và tạo file mới (xem CHANGELOG).
4. Tạo vùng WorldGuard tên `casino` bao quanh sòng (đổi tên trong `config.yml` > `access.regions`).

Không có ItemsAdder: GUI vẫn chạy nhưng dùng item thường (STICK) và tiêu đề chữ thường (`fallback-title`).
Icon `%img_money%` / `%img_gem%` cần PlaceholderAPI (ItemsAdder cung cấp các placeholder này).
Nút đổi tiền tệ dùng item `treasure:money_item` / `treasure:gem_item` của pack treasure trên server.

## Máy chơi

Đứng nhìn vào khối muốn đặt máy lên, gõ `/casino admin machine place <game>` (game: mines, plinko, stairs, roulette,
races, xidach, dice, rps, tictactoe). Người chơi chuột phải vào máy để mở game. Gỡ: nhìn vào máy, `/casino admin machine remove`.
Model lệch thì chỉnh `machines.y-offset` / `scale` / `display` trong `config.yml`, `/casino admin reload`, gỡ và đặt lại máy.

## NPC (ZNPCsPlus)

NPC chạy lệnh console với `{player}`:

```
/npc action add consolecommand <id NPC> ANY_CLICK 1 0 casino open {player} menu
/npc action add consolecommand <id NPC> ANY_CLICK 1 0 casino open {player} mines
/npc action add consolecommand <id NPC> ANY_CLICK 1 0 casino open {player} plinko
/npc action add consolecommand <id NPC> ANY_CLICK 1 0 casino open {player} stairs
/npc action add consolecommand <id NPC> ANY_CLICK 1 0 casino open {player} roulette
/npc action add consolecommand <id NPC> ANY_CLICK 1 0 casino open {player} races
/npc action add consolecommand <id NPC> ANY_CLICK 1 0 casino open {player} tictactoe
/npc action add consolecommand <id NPC> ANY_CLICK 1 0 casino open {player} xidach
```

(`1` = cooldown 1 giây, `0` = không delay.) Người chơi vẫn phải đứng trong vùng sòng, trừ khi bật
`access.npc-ignores-region: true`.

## Lệnh

| Lệnh | Ai dùng | Tác dụng |
|---|---|---|
| `/casino` | mọi người | Menu sòng (phải đứng trong vùng sòng) |
| `/casino thongke` | mọi người | Thống kê thắng thua |
| `/casino nghi <1h\|1d\|7d\|30d>` | mọi người | Tự khóa sòng (không tự mở lại được) |
| `/casino tieptuc` | mọi người | Vào lại trận PvP hoặc bàn xì dách đang chơi |
| `/dice` (`/xucxac`), `/oantuti` (`/kbb`) | mọi người, mọi nơi | Mở bàn 1v1 (mở kèo / nhận kèo) |
| `/dice <tiền> <tien\|gem>` | mọi người | Mở kèo cho ai cũng nhận được (`10k`, `2m`...) |
| `/dice <người> <tiền> <tien\|gem>` | mọi người | Thách đấu trực tiếp một người |
| `/casino open <người> <menu\|mines\|plinko>` | console / admin | Cho NPC mở game |
| `/casino admin reload` | admin | Nạp lại config (ván đang chơi được chốt/hoàn tiền trước) |
| `/casino admin toggle <game>` | admin | Đóng/mở 1 game đến lần reload sau |
| `/casino admin stats [game] [today\|7d\|all]` | admin | Sòng lời/lỗ bao nhiêu, RTP thực tế |
| `/casino admin simulate <game> [số ván]` | admin | Chạy thử ngầm để đo RTP |
| `/casino admin log <người> [trang]` | admin | Lịch sử cược |
| `/casino admin refund <mã cược>` | admin | Hoàn tiền một cược bị kẹt |
| `/casino admin alt <info\|allow\|reset> <người>` | admin | Luật 1 tài khoản / IP |
| `/casino admin unban <người>` | admin | Gỡ tự khóa |
| `/casino admin jackpot <money\|gem> [set\|add <số>]` | admin | Xem / chỉnh hũ jackpot |
| `/casino admin machine place <game>` / `remove` | admin | Đặt / gỡ máy chơi |
| `/casino admin debug` | admin | Kiểm tra ItemsAdder, item, hình nền GUI |

Quyền: `boxcasino.use`, `boxcasino.game.<game>` (mặc định có), `boxcasino.admin`,
`boxcasino.bypass.limits` (bỏ qua giờ chơi, giới hạn lỗ, tự khóa), `boxcasino.bypass.ip`.

## Placeholder

`%boxcasino_won_money%`, `%boxcasino_lost_gem%`, `%boxcasino_net_money%`, `%boxcasino_biggest_win_gem%`,
`%boxcasino_rounds_money%`, `%boxcasino_loss_left_money%`, `%boxcasino_playtime_left%`, `%boxcasino_rest_left%`,
`%boxcasino_jackpot_money%`, `%boxcasino_jackpot_gem%`, `%boxcasino_top_net_money_1_name%`, `%boxcasino_top_net_money_1_value%`
(1-10, money hoặc gem).

## An toàn tiền

- Không có chip thật: chip chỉ là hình. GUI không nhận và không trả item nào.
- Mỗi cược được ghi vào `data.db` **trước** khi trừ tiền. Server sập giữa ván thì lần khởi động sau tự hoàn
  tiền (hoặc trả nốt tiền thắng đã chốt). Console in một dòng `Ledger recovery: ...` khi có.
- Đóng GUI giữa ván không bao giờ bị xử thua: Dò Mìn / Leo Thang tự rút (chưa mở ô nào thì hoàn tiền),
  Plinko / Vòng Quay trả kết quả luôn, Đua Ngựa vẫn giữ cược đến khi đua xong.
- Gem chỉ cộng được khi người chơi online: nếu không cộng được thì lưu lại, trả khi người đó vào lại server.
