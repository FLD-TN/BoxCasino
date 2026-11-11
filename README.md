# BoxCasino

Sòng bạc của server BoxPvP. Người chơi cược bằng tiền trong game (Vault) hoặc Gem (BoxCore), không dùng tiền thật và
không có chip dạng item. Có game đấu Nhà Cái, game chung cả server và game 1v1 giữa hai người chơi.

Paper 1.21.1, Java 21. Cần Vault (kèm plugin kinh tế) và BoxCore. Không bắt buộc: WorldGuard (vùng sòng), ItemsAdder
(giao diện), PlaceholderAPI, Boombox (nhạc nền), ZNPCsPlus (NPC chia bài).

## Các game

| Game | Mã | Kiểu |
|---|---|---|
| Dò Mìn | `mines` | Đấu Nhà Cái, rút lúc nào cũng được |
| Kim Tự Tháp (Plinko) | `plinko` | Đấu Nhà Cái |
| Leo Thang | `stairs` | Đấu Nhà Cái, leo từng bậc, rút lúc nào cũng được |
| Roulette | `roulette` | Đấu Nhà Cái, bánh xe 37 số |
| Máy Xèng | `slots` | Đấu Nhà Cái |
| Rương Hải Tặc | `chest` | Đấu Nhà Cái |
| Vé Số | `lottery` | Đấu Nhà Cái |
| Xì Dách | `xidach` | Luật Việt Nam, tối đa 3 người một bàn đấu Nhà Cái |
| Đua Ngựa | `races` | Một cuộc đua chung cả server |
| Cắt Dây | `crash` | Một sợi dây chung cả server, rút trước khi đứt |
| Góp Hũ | `pot` | Người chơi góp chung một hũ, góp càng nhiều cơ hội càng cao |
| Xúc Xắc | `dice` | 1v1, chơi ở mọi nơi |
| Oẳn Tù Tì | `rps` | 1v1, chơi ở mọi nơi |
| Cờ Caro | `tictactoe` | 1v1, chơi ở mọi nơi |

Mỗi game có file riêng trong `games/` (tỉ lệ, mức cược, bố cục GUI). Ngoài ra còn hũ Jackpot (mỗi cược góp một phần
nhỏ, có xác suất nổ hũ), bảng xếp hạng, thông báo thắng lớn, nhạc nền và Giờ Vàng (thắng đấu Nhà Cái được cộng thêm %
tiền lời trong một khoảng thời gian, có bossbar cho cả server).

## Chơi có giới hạn

- Game trong sòng chỉ mở được khi đứng trong vùng WorldGuard `access.regions` (mặc định `casino`).
- Giới hạn giờ chơi: chơi đủ `play-minutes` thì phải nghỉ `rest-minutes`. Chỉ tính lúc đang mở GUI casino, ván đang
  chơi được chơi nốt.
- Giới hạn lời ròng mỗi ngày (`daily-profit-limit`): lời tới mức này thì không đặt ván mới được đến 0h.
- Người chơi tự khoá sòng bằng `/casino nghi <1h|1d|7d|30d>` và không tự mở lại được.
- Một tài khoản mỗi IP; giờ chơi tính chung theo tài khoản chính của IP. Admin cho phép từng trường hợp bằng
  `/casino admin alt allow`.

## An toàn tiền

- GUI không nhận và không trả item nào; chip chỉ là hình.
- Mỗi cược được ghi vào `data.db` trước khi trừ tiền. Server sập giữa ván thì lần khởi động sau tự hoàn tiền, hoặc trả
  nốt tiền thắng đã chốt. Console in dòng `Ledger recovery: ...` khi có.
- Đóng GUI giữa ván không bao giờ bị xử thua: Dò Mìn / Leo Thang tự rút (chưa mở ô nào thì hoàn tiền), Plinko / Roulette
  trả kết quả luôn, Đua Ngựa giữ cược đến khi đua xong. `/casino tieptuc` mở lại ván PvP hoặc bàn Xì Dách đang chơi.
- Gem chỉ cộng được khi người chơi online; không cộng được thì lưu lại, vào lại server sẽ nhận.
- `/casino admin reload` chốt hoặc hoàn tiền các ván đang chơi trước khi đọc lại config.

## Cài đặt

1. Bỏ jar vào `plugins/`, khởi động server.
2. Tạo vùng WorldGuard `casino` bao quanh sòng, hoặc đổi tên vùng trong `access.regions`.
3. Giao diện dùng font image của các content pack ItemsAdder `iavegasgui` (GUI) và `iavegastables` (máy chơi). Pack
   không có trong repo. Không có ItemsAdder thì GUI vẫn chạy với item thường và tiêu đề chữ (`fallback-title`).
4. Đặt máy chơi: đứng nhìn vào khối muốn đặt, `/casino admin machine place <game>`. Người chơi chuột phải vào máy để
   chơi. Model lệch thì chỉnh `machines.y-offset` / `scale` / `display`, reload rồi đặt lại máy.
5. NPC mở game: cho NPC chạy lệnh console `casino open {player} <menu|mã game>`. Người chơi vẫn phải đứng trong vùng
   sòng, trừ khi bật `access.npc-ignores-region`.

Có ZNPCsPlus thì bàn Xì Dách và Roulette có NPC chia bài (`machines.dealers`), vung tay khi chia / quay.

## Lệnh

| Lệnh | Quyền | Mô tả |
|---|---|---|
| `/casino` | `boxcasino.use` | Menu sòng |
| `/casino thongke` | `boxcasino.use` | Thống kê thắng thua của mình |
| `/casino tieptuc` | `boxcasino.use` | Mở lại ván đang chơi |
| `/casino nhac` | `boxcasino.use` | Bật / tắt nhạc |
| `/casino nghi <1h\|1d\|7d\|30d>` | `boxcasino.use` | Tự khoá sòng |
| `/casino giovang` | `boxcasino.use` | Xem Giờ Vàng còn bao lâu |
| `/casino giovang <phút> [%]`, `/casino giovang tat` | `boxcasino.giovang` | Bật / tắt Giờ Vàng (console luôn dùng được) |
| `/dice` (`/xucxac`), `/oantuti` (`/kbb`), `/caro` (`/tictactoe`) | `boxcasino.game.<game>` | Xem kèo đang mở |
| `/dice <tien\|gem> <mức cược>` | như trên | Mở kèo ai cũng nhận được (`10k`, `2m`...) |
| `/dice <người> <tien\|gem> <mức cược>` | như trên | Thách đấu một người |
| `/casino open <người> <menu\|game>` | `boxcasino.admin` | Mở game cho người khác (NPC) |
| `/casino admin reload` | `boxcasino.admin` | Đọc lại config |
| `/casino admin toggle <game>` | `boxcasino.admin` | Đóng / mở một game đến lần reload sau |
| `/casino admin stats [game] [today\|7d\|all]` | `boxcasino.admin` | Sòng lời / lỗ bao nhiêu |
| `/casino admin simulate <game> [số ván]` | `boxcasino.admin` | Chạy thử ngầm để đo tỉ lệ trả thưởng |
| `/casino admin log <người> [trang]` | `boxcasino.admin` | Lịch sử cược |
| `/casino admin refund <mã cược>` | `boxcasino.admin` | Hoàn tiền một cược bị kẹt |
| `/casino admin alt <info\|allow\|reset> <người>` | `boxcasino.admin` | Luật một tài khoản mỗi IP |
| `/casino admin unban <người>` | `boxcasino.admin` | Gỡ tự khoá |
| `/casino admin jackpot <money\|gem> [set\|add <số>]` | `boxcasino.admin` | Xem / sửa hũ Jackpot |
| `/casino admin machine place <game>\|remove\|refit [bán kính]` | `boxcasino.admin` | Đặt, gỡ, làm lại vùng bấm của máy |
| `/casino admin dealer` | `boxcasino.admin` | Áp lại skin cho NPC chia bài |
| `/casino admin debug` | `boxcasino.admin` | Kiểm tra ItemsAdder, item và nền GUI |

Quyền khác: `boxcasino.game.<game>` (mặc định có), `boxcasino.bypass.limits` (bỏ qua giờ chơi, giới hạn lời, tự khoá),
`boxcasino.bypass.ip` (bỏ qua luật một tài khoản mỗi IP).

## Placeholder

| Placeholder | Giá trị |
|---|---|
| `%boxcasino_<won\|lost\|net\|biggest_win\|rounds>_<money\|gem>%` | Thống kê của người xem |
| `%boxcasino_profit_left_<money\|gem>%` | Còn được lời bao nhiêu hôm nay |
| `%boxcasino_playtime_left%`, `%boxcasino_rest_left%` | Thời gian chơi còn lại / thời gian nghỉ còn lại |
| `%boxcasino_jackpot_money%`, `%boxcasino_jackpot_gem%` | Hũ Jackpot |
| `%boxcasino_top_net_<money\|gem>_<1-10>_<name\|value>%` | Top thắng |
| `%boxcasino_top_loss_<money\|gem>_<1-10>_<name\|value>%` | Top thua |
| `%boxcasino_giovang%`, `%boxcasino_giovang_percent%` | Giờ Vàng còn lại (rỗng khi tắt), % cộng thêm |

## Build

```
./gradlew build
```

Ra file `build/libs/BoxCasino-<version>.jar`. Repo chỉ chứa mã nguồn, không có jar nào ngoài
`gradle/wrapper/gradle-wrapper.jar` của Gradle. Trước khi build, đặt vào thư mục `libs/`:

- `boxcore-api-1.3.0.jar`: build từ [BoxCore](https://github.com/FLD-TN/BoxCore) (`boxcore-api/build/libs/`)
- `boombox-api-3.6.14.jar`: API của plugin Boombox
- `znpcsplus-api-2.1.0.jar`: API của plugin ZNPCsPlus
