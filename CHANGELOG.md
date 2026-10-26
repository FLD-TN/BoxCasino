# Changelog

## 0.14.0 - 2026-10-26

### 3 game mới (1 người chơi, đấu nhà cái, Tiền và Gem)
- **Vé Số** (`lottery`): mua vé = đặt cược, vé 15 ô, cào 5 ô, nhận cược x hệ số cao nhất đã cào (x0.05 - x7, hình
  số của pack). Bút chì = cào hết. Hoàn trả 95%. ESC giữa chừng: tự cào nốt và trả tiền.
- **Máy Xèng** (`slots`, Drums của Vegas): màn 5x5, 8 biểu tượng. Bốc trước hình trúng (ô vuông 2x2 / 3x3 / 4x4,
  chữ X nhỏ / lớn, hàng ngang, hàng giữa, cột dọc, đường chéo, kín màn) rồi vẽ màn hình đúng hình đó; biểu tượng
  chữ W nhân đôi thưởng. Hoàn trả 95,2%. Các cột dừng lần lượt, trúng thì che các ô còn lại để nổi hình trúng.
- **Rương Hải Tặc** (`chest`, Dead Man's Chest của Vegas): 3 độ khó, mở rương 3 lần, mỗi lần ra Vàng / Hệ số / Bẫy,
  nhận cược x tổng vàng x tích hệ số. Mỗi độ khó hoàn trả khoảng 95%. ESC: tự mở nốt và trả tiền.
- Kết quả của cả 3 game được quyết định lúc đặt cược và ghi vào sổ; min / max cược riêng ở `games/<id>.yml`.
- Máy trong world (model lottery / drums / deadmanschest của pack iavegastables, 1 ghế), permission
  `boxcasino.game.lottery|slots|chest`, mở bằng NPC `/casino open <người> lottery|slots|chest`.

### Khác
- Menu `/casino`: game chưa có trong `config.yml` của server thì lấy vị trí từ bản mặc định trong jar (game mới tự
  hiện, không phải sửa config). Trùng ô với game khác thì không hiện và báo trong console.
- `config.yml` (bản mặc định): thêm `menu.games`, `machines.seats.layouts`, `machines.bounds`, `machines.items` cho
  3 game. Bounds / model / ghế tự lấy từ jar nếu config server chưa có. Không đổi config-version.
- `messages.yml`: thêm `games.lottery`, `games.slots`, `games.chest` (tự lấy từ jar nếu thiếu).

## 0.13.5 - 2026-10-26

- Cờ Caro chuyển sang sảnh kèo giống Xúc Xắc / Oẳn Tù Tì: GUI rương liệt kê kèo, nút Tạo kèo (chọn Tiền / Gem rồi
  nhập mức cược vào chat), chơi ở mọi nơi, lệnh mới `/caro` (alias `/tictactoe`): `/caro`, `/caro <tien|gem> <mức cược>`,
  `/caro <người> <tien|gem> <mức cược>`. Kèo mới báo toàn server kèm nút nhận như 2 game kia. Bàn cờ khi đấu giữ nguyên.
- Máy Cờ Caro trong world không còn ghế: bấm máy mở sảnh kèo. Config cũ còn ghế Cờ Caro thì ngồi xuống cũng mở sảnh.
- `config.yml`: bỏ `tictactoe` khỏi `machines.seats.layouts` và `machines.table-games` (chữ nổi thành "Máy X").
- `messages.yml`: thêm `pvp.lobby.info-tictactoe`, sửa `games.tictactoe.info` (bỏ phần ghế). Không đổi config-version.

## 0.13.4 - 2026-10-26

- Database: thêm index `bets(round_id)`, `bets(created_at)`, `rounds(created_at)` (tự tạo khi khởi động). Việc xoá
  lịch sử cũ và kiểm tra "round không còn cược" không còn quét toàn bảng `bets`, không giữ khoá DB lâu làm đứng server.
- Ghi DB nhẹ hơn cho luồng chính: câu lệnh SQL được chuẩn bị 1 lần rồi dùng lại; mở cược (round + bet) gộp 1
  transaction; ván thua ghi thẳng DONE (bỏ bước SETTLING). Thống kê, sổ nhà cái, số thua trong ngày và hũ jackpot
  được cộng dồn trong RAM và ghi 1 transaction mỗi 5 giây (cũng ghi khi người chơi thoát, khi mở Thống kê và khi
  tắt plugin). Sổ cược (ledger) vẫn ghi ngay để an toàn khi crash.
- `getHolder(false)` ở mọi chỗ kiểm tra GUI: click trong rương / lò thường không còn tạo bản sao block.
- Mức cược nhập vào chat và lệnh `/xucxac`, `/kbb` chỉ nhận số thường (10000, 400000). Bỏ `k`, `m`, dấu `.` `,`
  (trước đây `1.5k` thành 15.000).
- Đang có kèo Xúc Xắc / Oẳn Tù Tì chờ người nhận thì không vào game khác (kể cả nhận kèo game khác): báo
  "Bạn đang có kèo ... huỷ kèo đó để chơi game khác (/casino pvp cancel <id>)".
- Mức cược đã nhớ của người chơi được xoá khi thoát server (trước đây giữ trong RAM đến khi restart).
- `messages.yml`: thêm `access.busy-challenge` (tự lấy từ bản trong jar nếu thiếu); sửa `pvp.lobby.prompt`,
  `pvp.lobby.prompt-invalid` (bỏ ví dụ 10k / 2m). Không đổi config-version.

## 0.13.3 - 2026-10-25

- Sửa lỗi nút chơi (thả bóng Kim Tự Tháp, quay Roulette, Dò Mìn, Leo Thang, Đua Ngựa, bàn kèo PvP) vẫn hiện mức
  cược / tiền tệ cũ sau khi đổi mức cược trong menu cược (bấm Xong hoặc ESC) hoặc bấm đổi $ / Gem. Giờ cả GUI vẽ lại
  ngay, không chỉ nút mức cược.
- Prefix mới: `[CASINO]` gradient Carmine #B32E33 sang Brass #CD9F65, ngoặc Bordeaux #6E151A (`messages.yml`, không
  đổi config-version, sửa tay dòng `prefix:` trên server).

## 0.13.2 - 2026-10-25

- Xúc Xắc / Oẳn Tù Tì không còn bàn: sảnh kèo ghi "Kèo của Duy" / "Kèo của bạn" thay cho số bàn; thông báo kèo mới
  "Xúc Xắc - Kèo của Duy [10.000$]". `messages.yml` lên config-version 8.

## 0.13.1 - 2026-10-25

- Thông báo bàn (Xì Dách / Đua Ngựa): không gửi cho chính người vừa đặt cược, người khác trong sòng (kể cả đang
  ngồi) vẫn nhận.
- Sảnh kèo Xúc Xắc / Oẳn Tù Tì làm lại theo chuẩn BoxClans: viền kính xám, ô giữa hàng trên là luật chơi (cách
  chơi, mức cược tiền / gem, thuế, lưu ý), 3 hàng giữa là các kèo, hàng dưới: trang trước - **Tạo kèo** - trang sau.
  Lore kèo chia mục Thông tin, thao tác ở cuối.
- Nút **Tạo kèo**: mở GUI nhỏ chọn Tiền / Gem (icon tiền của server, hiện số dư và mức cược cho phép), rồi nhập mức
  cược vào chat (dòng đó không hiện cho ai, gõ `huy` để huỷ, hết hạn sau 60 giây). Đang có kèo thì nút báo
  "Bạn đang có kèo".
- Lệnh `/xucxac`, `/kbb`: không gợi ý số tiền nữa (chỉ gợi ý tien / gem và tên người chơi).
- `messages.yml` lên config-version 7.

## 0.13.0 - 2026-10-25

### Bỏ trò chuyện
- Bỏ toàn bộ tính năng chat trong game: nút chat, phím Q, phòng chat dạng sách, câu nói trên đầu, `/casino chat`.
  Giữ thông báo "Bạn vừa rời bàn, ván vẫn đang chạy [Quay lại bàn]" và `/casino tieptuc`.

### Số bàn / số máy
- Mỗi máy được đánh số khi đặt (tăng dần theo từng game, lưu ở `machines.yml`). Chữ nổi: "XÌ DÁCH - Bàn 2"
  (game nhiều người: `machines.table-games`, mặc định xidach, tictactoe), các máy khác "DÒ MÌN - Máy 3".
- Bàn Xì Dách của máy mang số của máy; bàn mở từ menu / NPC đánh số tiếp sau số máy cao nhất.
- Máy đã đặt: `/casino admin machine refit` để được đánh số.

### Thông báo bàn
- Xì Dách: "Xì Dách - bàn 2 sẽ chia bài sau 15 giây → Ghế trống còn 2/3" (số ghế xanh khi còn, đỏ khi hết).
- Đua Ngựa: "Đua Ngựa sẽ xuất phát sau 30 giây → Duy vừa đặt cược".
- Cờ Caro ở máy: "Cờ Caro - bàn 1 - Duy đang chờ đối thủ [10.000$] → Ghế trống còn 1/2".
- Gửi cho mọi người trong vùng casino, kể cả người đang ngồi. Cùng một bàn chỉ báo lại sau 30 giây
  (`broadcast.table-cooldown`) - đặt rồi huỷ cược liên tục không spam được.

### Xúc Xắc / Oẳn Tù Tì: sảnh kèo, không cần bàn
- `/xucxac <tien|gem> <mức cược>` (hoặc `/kbb ...`) mở kèo; `/xucxac <người> <tien|gem> <mức cược>` thách 1
  người; `/xucxac` không kèm gì mở **sảnh kèo**. Thứ tự tiền tệ / mức cược đảo được; mức cược giữ min / max cũ.
- Sảnh kèo: GUI rương thường (không cần pack), đầu người mở kèo kèm lore mức cược, tiền thắng, thời gian còn lại,
  số kèo ("bàn X"); bấm để nhận kèo, kèo của mình bấm để huỷ. Tự cập nhật mỗi giây, có trang nếu nhiều kèo.
- Kèo mới báo cả server có nút [Nhận kèo] (như cũ) kèm số bàn.
- Nhận kèo thì GUI game (pack) mở ra, chỉ còn phần chơi (không còn nút cược / tiền tệ / luật / chat).
- Không còn ghế / Shift cho 2 game này (bỏ layout ghế); máy Xúc Xắc / Oẳn Tù Tì đã đặt bấm vào mở sảnh kèo.
  ESC lúc đang chơi: game tự chơi, kết quả báo ở chat.

### Cài đặt
- `messages.yml` lên config-version 6 (file cũ chuyển vào `old-configs/`).
- Máy Xúc Xắc / Oẳn Tù Tì đã đặt còn ghế cũ: `/casino admin machine refit` để gỡ ghế.

## 0.12.2 - 2026-10-25

- Chữ nổi trên máy: thêm khoảng cách tới viền nền. `machines.hologram.padding` (dấu cách 2 bên mỗi dòng, mặc định
  3) và `padding-lines` (dòng trống trên / dưới, mặc định 0). Máy đã đặt cập nhật khi reload / có người ngồi.

## 0.12.1 - 2026-10-25

- Nhà cái không có skin: ZNPCsPlus gửi png lên MineSkin bằng API cũ không có key, MineSkin từ chối. Thêm
  `machines.dealers.texture` / `signature` (lấy ở mineskin.org) - có thì dùng thẳng, không cần MineSkin lúc chạy.
- Lệnh `/casino admin dealer`: đổi skin cho mọi nhà cái theo config (sau khi dán texture / signature và reload).

## 0.12.0 - 2026-10-25

### Âm thanh / nhạc
- Bỏ nút nhạc trong GUI mọi game. Âm thanh trong game (bấm nút, thắng, thua) luôn có, tắt chung bằng
  `config.yml` > `game-sounds`; người chơi không muốn nghe thì chỉnh âm lượng trong game.
- Đoạn nhạc thắng / thua / hoà / nổ hũ phát trong mọi game (`music.results-enabled`).
- Nhạc nền chỉ phát trong vùng casino (`music.area`); ai không muốn nghe gõ `/casino nhac`. `music.in-gui: true` để
  phát cả khi mở GUI ngoài vùng casino.

### Mỗi lần chỉ chơi 1 game
- Bỏ "Shift + chuột để về menu sòng" ở ô luật mọi game.
- Đang ngồi ở máy, đang có trận 1v1 chưa xong, hoặc đang có bài Xì Dách: không mở được `/casino`, game khác, NPC
  hay lệnh /dice /kbb ("mỗi lần chỉ chơi 1 game").

### ESC / Shift theo từng game
- Kim Tự Tháp, Vòng Quay: bóng đang rơi / vòng đang quay thì ESC không thoát, GUI tự mở lại sau 8 tick
  (`esc-reopen-ticks`). Đang ngồi ở máy mà kịp bấm Shift đứng dậy trong lúc đó thì bỏ qua, ra kết quả ngay.
- Dò Mìn, Leo Thang: ESC = rút tiền theo hệ số hiện tại (như cũ).
- Xì Dách ở máy: ESC = thoát giao diện, hệ thống tự chơi; **Shift đứng dậy = bỏ cuộc, mất tiền cược** (chưa chia
  bài thì hoàn tiền).
- Xúc Xắc / Oẳn Tù Tì / Cờ Caro ở máy: trước khi vào trận ESC / Shift bình thường; đang trong trận ESC thì GUI tự
  mở lại, Shift đứng dậy = thua. Chơi từ xa: ESC thoải mái, game tự chơi, kết quả báo ở chat.
- Ô luật / lưu ý của các game viết lại theo đúng các điều trên.

### Ghế
- Mặc định `machines.seats`: `sit-height: 0.8`, `scale: 1.3`.

### Chữ nổi trên máy
- Mỗi máy có chữ nổi phía trên model: tên game in hoa (màu của game) và "Ghế trống: X/Y" (X xanh khi còn ghế,
  đỏ khi hết). Đua Ngựa (không ghế): "Chuột phải vào máy để chơi". Cập nhật khi có người ngồi / đứng dậy.
- `machines.hologram`: bật / tắt, độ cao, cỡ chữ, bóng chữ, màu nền. Chữ ở `messages.yml` > `machine.holo`.

### NPC nhà cái (cần ZNPCsPlus)
- Máy Xì Dách: nhà cái đứng sau bàn, đối diện người chơi. Máy Vòng Quay: đứng bên phải máy.
- Skin `casino_dealer.png` (chép sẵn vào `plugins/ZNPCsPlus/skins`, ZNPCsPlus đổi sang skin qua MineSkin ở lần
  đầu), tên trên đầu "Nhà Cái", quay đầu nhìn người chơi gần nhất, vung tay khi chia bài / quay vòng.
- `machines.dealers`: bật / tắt, tên, file skin, vị trí từng game.

### Hiệu ứng thắng
- Thắng bất kỳ game nào: pháo hoa bắn lên từ người thắng (không gây sát thương) và tiếng "rẹt rẹt" đếm tiền
  (âm thanh vanilla ghép lại; đổi được sang file .ogg trong pack). `effects.win` trong config.yml.

### Cài đặt
- Máy đã đặt: `/casino admin machine refit [bán kính]` để thêm chữ nổi và nhà cái.
- `messages.yml`: các chữ mới tự lấy mặc định; chữ luật / lưu ý cũ trong file của server cần chép lại từ jar
  (hoặc xoá file để plugin tạo lại).

## 0.11.1 - 2026-10-24

### Nhạc thật (.ogg) trong resource pack
- `pack/boxcasino`: `sounds/music/casino_1.ogg` (stereo, 1:42) + `sounds.json` (âm thanh `boxcasino:music.casino_1`,
  stream). Chép lại `pack/boxcasino` rồi `/ia zip`.
- Nhạc nền giờ nhận 2 loại: bài trong pack `{sound: 'boxcasino:music.casino_1', length: 102}` (không cần Boombox)
  và bài .nbs (qua Boombox, không có thì bỏ qua). Bài mặc định là `casino_1`, chuột phải nút nhạc để đổi sang
  các bài .nbs của Vegas.
- `music.area: true`: nhạc nền phát cả khi chỉ đứng trong vùng casino (không cần mở GUI), ra khỏi vùng 3 giây thì
  tắt. Lệnh `/casino nhac` bật / tắt khi không mở GUI.
- Nhạc thắng / thua vẫn là .nbs (cần Boombox), phát chồng lên bài .ogg (bài .ogg không tạm dừng được).
- Âm lượng theo thanh "Jukebox/Note Blocks" trong cài đặt âm thanh của người chơi.

## 0.11.0 - 2026-10-24

### Nhạc trong game (cần plugin Boombox)
- Nhạc nền lặp lại khi đang mở GUI sòng (4 bài nhạc nền của Vegas), mỗi kết quả có đoạn nhạc ngắn rồi nhạc nền phát
  tiếp: thắng (victory), thua (defeat), hoà (draw), thắng lớn từ x5 (bonus), nổ hũ (jackpot).
- Nút nhạc trong mọi game (texture `*_music_active` / `*_music_inactive` của pack Vegas): chuột trái bật / tắt,
  chuột phải đổi bài nhạc nền. Lựa chọn nhớ theo người chơi (`music-players.yml`), người mới mặc định bật.
- Rời GUI 3 giây thì nhạc dừng (đổi menu / mở phòng chat không làm ngắt nhạc).
- Không cài Boombox: nút nhạc ẩn, mọi thứ khác chạy bình thường.
- File nhạc chép ra `plugins/BoxCasino/music/*.nbs`; thêm bài: chép .nbs vào đó và ghi tên vào `config.yml` > `music`.

### Bố cục nút
- Dò Mìn: nhạc bên trái nút luật (ô 51).
- Xì Dách: nhạc góc trên bên phải (ô 8, ô 0 là ghế 1); nút chat lên trên nút luật (ô 44).
- Kim Tự Tháp: nhạc dưới nút mức cược (ô 9). Leo Thang: trên nút đổi tiền (ô 7). Vòng Quay: dưới nút đổi tiền (ô 41).
- Đua Ngựa: nhạc ở chỗ cũ của nút mức cược (ô 45), nút mức cược sang bên trái nút đổi tiền (ô 51).
- Xúc Xắc / Oẳn Tù Tì / Cờ Caro: **trước đây thiếu thanh cược** (chỉ cược được mức thấp nhất). Giờ có đủ mức cược,
  đổi tiền tệ, luật chơi và nhạc:
  - Xúc Xắc: 45 cược, 46 tiền tệ, 52 nhạc, 53 luật (2 góc dưới).
  - Oẳn Tù Tì: 0 cược, 1 tiền tệ, 7 nhạc, 8 luật (2 góc trên).
  - Cờ Caro: 36 cược, 37 tiền tệ, 43 nhạc, 44 luật (2 góc dưới).
- `games/*.yml` lên config-version 4 (file cũ chuyển vào `old-configs/`, chép lại phần đã sửa tay nếu có).
- Ô bet / currency / info thiếu trong file của server thì lấy mặc định trong jar.

## 0.10.0 - 2026-10-24

### Ghế ở máy chơi
- Máy đặt bằng `/casino admin machine place` có thêm ghế (pack `boxcasino`: `casino_chair`, `casino_stool`, model và
  hình lấy từ bộ heromarine, plugin tự đặt, không dùng furniture của ItemsAdder):
  - Dò Mìn, Kim Tự Tháp, Leo Thang, Vòng Quay: 1 ghế tựa phía trước. Có người ngồi thì người khác bị chặn
    ("Máy đang có người chơi").
  - Xì Dách: 3 ghế đẩu phía trước, ghế 1-2-3 ứng với ghế 1-2-3 trong GUI.
  - Xúc Xắc, Oẳn Tù Tì, Cờ Caro: 2 ghế đẩu đối diện nhau (trước / sau máy).
  - Đua Ngựa: không ghế, bấm máy mở game như cũ.
- Chuột phải ghế = ngồi đúng ghế đó; chuột phải thân máy / barrier = ngồi ghế trống ngẫu nhiên; ngồi xong GUI mở,
  người chơi quay mặt vào máy. ESC / E chỉ đóng GUI (chuột phải máy để mở lại). **Shift = đứng dậy**, rời bàn.
- Xì Dách: mỗi máy là một bàn riêng, chỉ người ngồi ghế của máy đó ở bàn; đứng dậy khi đang có cược thì ván vẫn
  chơi tự động, ghế trả lại sau ván. Bàn mở từ menu / NPC vẫn là bàn ảo như cũ.
- Xúc Xắc / Oẳn Tù Tì / Cờ Caro ở máy tách hẳn với chơi từ xa:
  - Người ngồi trước mở kèo (mức cược + tiền), báo cho người **trong vùng sòng**: "X đang chờ đối thủ ở bàn
    Xúc Xắc [100.000$] trong sòng" (chữ thường, không nút).
  - Người ngồi ghế đối diện thấy kèo ở ô bên phải, xem mức cược rồi mới bấm nhận (không tự bắt đầu).
  - Chỉ 2 người ngồi ở máy đó chơi được kèo đó; người đang ngồi không nhận kèo từ xa và ngược lại.
  - **Đứng dậy giữa ván = xử thua**; đứng dậy khi kèo chưa ai nhận = huỷ kèo, hoàn tiền.
  - Hết ván, 3 giây sau cả hai quay lại bàn của máy để chơi tiếp.
- `config.yml` > `machines.seats`: hình ghế, khoảng cách `gap`, độ cao ngồi `sit-height`, `scale`, `yaw-offset`,
  `layouts` từng game. Máy đã đặt: `/casino admin machine refit [bán kính]` để thêm ghế.
- Pack: chép lại `pack/boxcasino` rồi `/ia zip`.

## 0.9.1 - 2026-10-24

### Máy trong world: bấm vào model là mở game
- Lỗi cũ: model (HEAD) treo thấp hơn vị trí máy khoảng 2,2 khối, còn ô bấm + barrier chỉ có 1 ô 1x1,6 ở vị trí máy.
  Máy cao (Dò Mìn, Kim Tự Tháp...) chỉ bấm được phần đầu; bàn thấp (Xì Dách, Vòng Quay, Xúc Xắc...) phải ngước lên
  bấm vào khoảng không phía trên bàn.
- Giờ:
  - Đặt máy mới: model **đứng đúng trên khối** đang nhìn (không còn lún / lơ lửng).
  - **Vùng bấm phủ toàn bộ model** theo kích thước thật đo từ pack (`machines.bounds`): máy cao bấm được cả thân,
    bàn rộng như Xì Dách (4,5 x 2,4 khối) có nhiều ô bấm ghép lại.
  - **Barrier nằm dưới chân model** (theo diện tích model; 1 lớp cho bàn thấp, 2 lớp cho máy cao), bấm vào barrier
    cũng mở game.
- Lệnh mới `/casino admin machine refit [bán kính]` (mặc định 30): làm lại vùng bấm + barrier cho các máy **đã đặt**
  quanh bạn, model giữ nguyên chỗ, barrier cũ lơ lửng bị xoá.
- `config.yml`: bỏ `y-offset`, `click-width`, `click-height`; thêm `y-adjust`, `bounds`. Không cần sửa file: thiếu
  key mới thì plugin dùng mặc định, key cũ bị bỏ qua. `scale: 0.625` = đúng cỡ như Vegas gốc (hiện model to hơn
  Vegas 1,6 lần).

## 0.9.0 - 2026-10-24

### Menu chọn mức cược (thay cách bấm trái/phải/Shift + đổi bước)
- Nút **Mức cược** trong mọi game: bấm là mở menu **Chọn mức cược** (nền `shop_gui` của pack Vegas, chữ SHOP đổi
  thành **CƯỢC**):
  - Hàng 2: `[Trừ 100.000] [Trừ 10.000] [Trừ 1.000]` - mức đang chọn - `[Cộng 1.000] [Cộng 10.000] [Cộng 100.000]`.
  - Hàng 3: các mức có sẵn (hình chip, mức ngoài giới hạn của game bị ẩn), mức đang chọn có thẻ `[đang chọn]`.
  - Hàng 4: **Tối thiểu**, **Chia đôi**, **Gấp đôi**, **Tối đa** (giới hạn game hoặc số dư nếu ít hơn), tên nút ghi sẵn
    số tiền sẽ ra.
  - Hàng 5: **Xong** (ổ khoá bên phải nền cũng là Xong), ESC cũng quay lại game.
  - Mức cược vẫn nhớ riêng cho từng game và từng loại tiền.
- Nút **Cược bằng** giờ chỉ đổi Tiền / Gem (bỏ chuột phải đổi bước).
- Mở menu chọn mức cược **không rời bàn**: Xì Dách giữ ghế, Đua Ngựa / bàn PvP vẫn chạy. Bị đóng vì lý do khác
  (hết giờ chơi, thoát game, reload) thì bàn cũng đóng như trước.
- `config.yml` thêm mục `bet-menu` (steps, presets, items); `chips` giờ chỉ là hình. Không đổi config-version: thiếu
  mục này thì plugin dùng mặc định.
- `messages.yml` lên config-version 5 (file cũ chuyển vào `old-configs/`).
- Pack: `pack/iavegasgui` (shop_gui.png chữ CƯỢC) và `pack/boxcasino` (7 icon nút bet_*). Chép cả hai rồi `/ia zip`.

## 0.8.0 - 2026-10-23

### Phòng chat dạng sách (thay toàn bộ cách chat cũ)
- Nút **Trò chuyện**, phím Q ở bất kỳ ô nào, hoặc `/casino chat`: mở **phòng chat** của bàn bằng giao diện sách
  (không cần pack, đủ dấu tiếng Việt).
  - Trang 1: tiêu đề + số người, các tin gần nhất của cả bàn (mới nhất ở dưới, tên mình màu xanh lá, người khác
    xanh dương; tin dài quá 2 dòng bị cắt "..."), rồi 4 nút:
    `[Nhập tin nhắn]` `[Nói nhanh]` `[Làm mới]` `[Quay lại bàn]`.
  - `[Nhập tin nhắn]`: sách đóng, bấm T rồi gõ; gửi xong phòng chat tự mở lại.
  - `[Nói nhanh]`: trang 2 (11 câu mỗi trang), bấm 1 câu là gửi rồi quay lại phòng chat.
  - Đang mở phòng chat mà người khác nhắn: sách tự làm mới + tiếng "ting". Server không biết khi nào sách đóng nên
    coi như đã đóng khi người chơi xoay chuột, đi, bấm chuột hoặc mở GUI khác (không tự bật lại sau đó).
- `config.yml` > `chat.global` (mặc định true): true = tin ra chat chung như tự chat; false = tin gõ / nói nhanh từ
  phòng chat chỉ người cùng bàn thấy (`chat.room-format`).
- Bỏ: bảng nói nhanh dạng GUI, màn hình đe (cả hình nền `chat_anvil` trong `pack/boxcasino`), Shift ra khung chat.
  Giữ: câu vừa nói hiện trên đầu / ghế 5 giây (phát sáng), mục "Vừa nói:" khi rê chuột, tiếng "ting".
- `messages.yml` lên config-version 4 (file cũ chuyển vào `old-configs/`). `config.yml` giữ version 2: key
  `chat.quick-item` không còn dùng, `chat.global` tự lấy mặc định nếu chưa có.
- Pack: chép lại `pack/boxcasino` và chạy `/ia zip` (chỉ bỏ hình nền đe, có thể bỏ qua).

## 0.7.3 - 2026-10-23

- Màn hình gõ tin: ô giữa thành nút **Quay lại bàn** (barrier, khung đỏ trên hình nền), bấm = huỷ tin, quay lại bàn
  (giống ESC). Bỏ mũi tên vàng giữa các ô. Chép lại `pack/boxcasino` và chạy `/ia zip`.
- Pack: viền nút Gửi đổi sang **xanh lá** (cùng màu chữ "Gửi"), nút Quay lại giữ viền đỏ.

## 0.7.2 - 2026-10-23

- Màn hình gõ tin: bong bóng chat thu nhỏ (16x16) và đặt **bên trong** đầu ô gõ như icon của ô nhập; ô gõ kéo dài sang
  trái; tiêu đề thẳng mép trái ô gõ. Chép lại `pack/boxcasino` và chạy `/ia zip`.

## 0.7.1 - 2026-10-23

- `[Chuột phải để viết tin]` dùng **màn hình đe ảo** thay cho biển báo tạm: không đặt khối nào trong world,
  mở được ở mọi chỗ đứng, gõ 1 dòng tối đa 50 ký tự.
  - Hình nền riêng (font image `boxcasino:chat_anvil`, tông tím vàng như menu sòng): bong bóng chat thay búa,
    ô gõ viền vàng, nút gửi khung vàng; bỏ dấu cộng / mũi tên của đe. Tiêu đề "Nhập tin nhắn vào ô dưới".
  - Ô trái chứa item trong suốt tên rỗng (ô gõ bắt đầu trống), ô giữa không dùng, ô phải là nút **Gửi** hiện nội dung
    đang gõ. Không tốn kinh nghiệm. Mọi click khác bị chặn; khi đóng, đe được xoá sạch (không ai lấy được item).
  - ESC = huỷ, quay lại bàn. Gửi xong tin ra chat chung (như tự chat) rồi bàn tự mở lại.
- Pack `pack/boxcasino` thêm hình nền `gui/chat_anvil.png` (chạy lại `/ia zip`).

## 0.7.0 - 2026-10-23

### Trò chuyện trong bàn (Xì Dách, Đua Ngựa, Xúc Xắc, Oẳn Tù Tì, Cờ Caro)
- Nút **Trò chuyện** (icon bong bóng chat, pack mới `pack/boxcasino`):
  - `[Chuột trái]` nói nhanh: bảng 12 câu có sẵn (sửa ở `config.yml` > `chat.quick-phrases`).
  - `[Chuột phải]` viết tin: mở màn hình sửa biển báo (4 dòng). Paper 1.21.1 chưa có biển báo ảo nên plugin đặt tạm
    1 biển báo ở ô trống trên đầu người chơi, gõ xong (hoặc sau 60 giây / thoát game / tắt plugin) trả lại như cũ;
    biển này không phá được và không rơi ra item.
  - `[Shift + chuột]` ra khung chat: đóng bàn, bấm T chat bình thường, gửi xong tự mở lại bàn.
  - Phím **Q** ở bất kỳ ô nào trong bàn = nói nhanh.
- Mọi câu được gửi **như người chơi tự chat** (`player.chat`): đi qua plugin chat của server (định dạng, rank, mute...),
  hiện trong chat chung. Không có lọc riêng của BoxCasino.
- Câu nói của người đang ngồi bàn (kể cả chat thường) hiện **trên đầu / ghế người nói** trong GUI người cùng bàn
  5 giây (phát sáng + tên kèm câu nói), rê chuột xem 3 câu gần nhất. Đua Ngựa (không có đầu người chơi): 3 câu mới
  nhất hiện dưới nút đếm ngược.
- Rời bàn giữa ván: chat hiện nút `[Quay lại bàn]`. `/casino tieptuc` vào lại bàn Đua Ngựa khi đang có cược.
- Config cũ trên server không cần tạo lại: vị trí nút / item / chữ mới tự lấy giá trị mặc định trong jar.

## 0.6.0 - 2026-10-23

### Chữ và lore viết lại theo chuẩn BoxCore / BoxClans
- Không in đậm. Tên game viết hoa từng chữ, mỗi game một màu riêng (Nhà Cái `#E056FD`, Xì Dách `#70A1FF`,
  Đua Ngựa `#7BED9F`...). Thẻ phụ trong ngoặc tối: `Mức cược [1.000$]`.
- Lore chia mục `Cách chơi:` / `Thông tin:` / `Lưu ý:`, dòng `• Nhãn: giá trị`; thao tác ở cuối dạng
  `[Chuột trái để tăng cược]`.
- Tin nhắn: `✔` thành công, `✘` lỗi, `(!)` chú ý, không chấm cuối câu; số tiền / hệ số làm nổi.
- Ghi rõ chữ khó hiểu: "Bước" -> "Mỗi lần bấm: ±1.000$", "Giữ Shift: ±10.000$"; "Hoàn trả" -> "Tỉ lệ hoàn trả" kèm giải thích.
- Nút đổi tiền tệ dùng item icon của server: `treasure:money_item` / `treasure:gem_item`.

### PvP (Xúc Xắc, Oẳn Tù Tì, Cờ Caro): bỏ sảnh riêng, dùng bàn Vegas
- Mở game là vào bàn (đúng texture) có thanh cược. Ghế trái = bạn: `[Chuột trái để mở kèo]`, `[Chuột phải để huỷ kèo]`.
  Ghế phải = kèo người khác: `[Chuột trái để nhận kèo]`, `[Chuột phải để xem kèo khác]`. Có người nhận thì vào trận ngay.

### Thông báo
- Mở kèo Xúc Xắc / Oẳn Tù Tì: báo cả server kèm nút `[Nhận kèo]`. Mở kèo Cờ Caro: báo người trong sòng (chỉ chữ).
- Đua Ngựa / Xì Dách bắt đầu đếm ngược: báo người trong sòng (chỉ chữ). Tắt bằng `broadcast.challenges` / `broadcast.tables`.

### Nâng cấp
- `messages.yml` và `games/*.yml` lên `config-version: 3`: file cũ được chuyển vào `old-configs/`, file mới tự tạo.
  `config.yml` giữ nguyên (2 tuỳ chọn mới tự dùng giá trị mặc định).

## 0.5.2 - 2026-10-23

- Prefix chat đổi sang chữ **Casino** chuyển màu tím -> hồng (đồng bộ nền tím của menu `/casino`).

## 0.5.1 - 2026-10-23

- Xì Dách: ghế của bạn khi chưa cược **nhấp nháy** (chip phát sáng đúng loại tiền <-> hình ghế, mỗi nửa giây),
  ghế trống của người khác trong suốt, thanh hành động nhắc "Bấm vào ghế đang nhấp nháy bên trái để đặt cược".
  Item mới `items.seat-free` trong `games/xidach.yml` (file cũ tự dùng giá trị mặc định, không cần sửa).

## 0.5.0 - 2026-10-23

### Giao diện mới (theo phong cách BoxClans)
- Viết lại toàn bộ `messages.yml`: màu hex nổi bật, tên in đậm, lore chia khối, dòng `•` nhãn xám / giá trị trắng,
  Tiền xanh lá, Gem tím, hệ số vàng cam, dòng thao tác "Chuột trái: ..." màu xanh dương.
- Nút đổi tiền tệ: hình chip (chip xanh = Tiền, chip tím = Gem) thay cho cái loa; tên dùng `%img_money%` / `%img_gem%`
  (icon ItemsAdder qua PlaceholderAPI).
- Menu `/casino` xếp lại, mỗi game cách nhau 1 ô: hàng 2 game 1 người (Dò Mìn, Kim Tự Tháp, Leo Thang, Vòng Quay),
  hàng 3 bàn chung (Đua Ngựa, Xì Dách), hàng 4 đấu 1v1 (Xúc Xắc, Oẳn Tù Tì, Cờ Caro);
  hàng cuối: Thống kê, Bảng xếp hạng, Hồ sơ, Hũ Jackpot, Tự khóa.
- Mọi game: nút thông tin ghi rõ "ESC: thoát (không mất tiền)"; **Shift + click nút thông tin** = về menu sòng.
- Pack riêng `BoxCasino/pack/iavegasgui`: chữ **VEGAS** trên menu thành **CASINO**, chữ **DEALER** trên bàn Xì Dách
  thành **NHÀ CÁI** (vẽ lại pixel cùng kiểu chữ gốc).

### Tính năng mới
- **Hũ Jackpot**: mỗi loại tiền 1 hũ. 1% tiền thua ở trò nhà cái (không tính 1v1) vào hũ; mỗi ván trò nhà cái cược
  từ 10.000$ / 10 Gem có 0,05% cơ hội nổ cả hũ. Thông báo + title cả server. `/casino admin jackpot <money|gem> [set|add <số>]`.
- **Thông báo thắng lớn** cả server: lời từ 1.000.000$ / 1.000 Gem, hoặc hệ số từ x10 (config `broadcast`).
- **Bảng xếp hạng**: top 10 lời nhiều nhất mỗi loại tiền (menu dạng kim tự tháp, cập nhật 5 phút).
  Placeholder `%boxcasino_top_net_<money|gem>_<1-10>_<name|value>%`, `%boxcasino_jackpot_<money|gem>%`.
- **Máy chơi trong world**: `/casino admin machine place <game>` (nhìn vào khối bên dưới), `/casino admin machine remove`.
  Model từ pack `iavegastables`, bấm chuột phải vào máy để mở game. Lưu ngay trên entity, không cần file riêng.
- `/casino admin debug`: kiểm tra kết nối ItemsAdder, item và hình nền GUI.

### Sửa lỗi
- Hết spam cảnh báo `Couldn't look up profile properties` (Mojang timeout): đầu người chơi lấy skin có sẵn trên server,
  không hỏi Mojang nữa.

### Nâng cấp từ bản cũ
- Các file `config.yml`, `messages.yml`, `games/*.yml` có `config-version: 2`. File cũ (không có số này) được **chuyển
  vào `plugins/BoxCasino/old-configs/`** rồi tạo file mới. Muốn giữ chỉnh sửa cũ thì chép lại từ đó. `data.db` không đổi.

## 0.4.0 - 2026-10-23

### Game mới: Xì Dách (`xidach`)
- Luật Việt Nam, **3 người / bàn** đấu nhà cái (texture `blackjack_gui` có 3 ghế). Mở game là được xếp vào bàn còn
  ghế trống; hết ghế thì tự mở bàn mới.
- Bấm ghế của mình để cược. Người cược đầu tiên bắt đầu đếm ngược 15 giây, rồi chia bài từ **bộ 52 lá xáo mới mỗi
  ván** (thứ tự bộ bài lưu vào ledger).
- Lá A: 2 lá = 11/10/1, 3 lá = 10/1, 4-5 lá = 1. Xì bàn x3, xì dách x2, ngũ linh x2, thắng thường x2, hòa hoàn tiền.
- Nhà cái có xì bàn / xì dách ngay khi chia: kết thúc ván ngay.
- Các vòng hành động diễn ra cùng lúc, 15 giây mỗi vòng: Rút, Dằn (phải đủ 16), Gấp đôi x2 (lượt đầu, rút đúng
  1 lá; dưới 16 là non, thua), Bỏ bài (lượt đầu, nhận lại 50%). Hết giờ hoặc đóng bảng: đủ 16 tự dằn, chưa đủ tự rút.
- Bài của mình luôn ngửa; bài người khác úp đến khi nhà cái lật. Nhà cái ngửa 1 lá, úp 1 lá.
- Gấp đôi: tiền cược thêm được ghi vào ledger trước khi trừ (`BetService.raise`).
- Tắt/reload plugin giữa ván: hoàn tiền mọi cược của ván đó.

### Thay đổi luật so với spec (chống lạm phát)
- Mô phỏng 3 triệu ván cho thấy luật đã chốt (nhà cái dừng ở 16, cả hai quắc thì hòa) trả người chơi **106%**,
  tức nhà cái lỗ và in thêm tiền vào server.
- Mặc định mới: **nhà cái rút tới 17**, **người chơi quắc là thua kể cả khi nhà cái quắc** -> 97,7%.
- Theo yêu cầu chủ server (giữ thắng thường x2): **xì dách x2**, **ngũ linh x2** (xì bàn vẫn x3) -> **93,7%**.
- Các giá trị này ở `games/xidach.yml` (`dealer-min-stand`, `both-bust-draw`, `payouts`).

### Khác
- `/casino tieptuc` vào lại bàn xì dách đang có tiền cược.
- Menu `/casino`: Xì Dách ở ô 21.

## 0.3.0 - 2026-10-23

### Khung PvP (1v1)
- **Kèo**: tạo kèo mở (ai cũng nhận được) hoặc thách đấu trực tiếp (nút `[Chấp nhận]` `[Từ chối]` trong chat).
  Tiền người tạo bị giữ ngay khi tạo. Kèo mở tự huỷ sau 5 phút, lời mời trực tiếp sau 60 giây; huỷ/hết hạn đều hoàn tiền.
  Người tạo thoát game thì kèo huỷ và hoàn tiền.
- **Sảnh kèo** (rương thường): danh sách kèo (bấm để nhận, bấm kèo của mình để huỷ), thanh cược, nút tạo kèo,
  nút quay lại trận đang đấu.
- **Thuế** 5% trên tổng tiền 2 bên (người thắng nhận 2 x cược x 0,95). Hòa hoàn tiền, không thuế.
- Mỗi người chỉ 1 trận một lúc; khi trận bắt đầu, mọi kèo khác của 2 người tự huỷ và hoàn tiền.
- Đóng bảng không bao giờ bị xử thua: trận vẫn chạy, hết giờ thì tự đi giúp. `/casino tieptuc` để vào lại.
- Tắt/reload plugin: Xúc Xắc (kết quả đã định) được trả; Oẳn Tù Tì / Cờ Caro đang dở thì hoàn tiền cả hai.

### Game mới
- **Xúc Xắc** (`dice`, thay Coinflip): mỗi bên 2 xúc xắc, tổng cao hơn thắng, hòa tự tung lại (tối đa 10 lần).
  Mọi lần tung được bốc sẵn lúc bắt đầu và lưu vào ledger. **Chơi ở mọi nơi**: `/dice` (`/xucxac`).
- **Oẳn Tù Tì** (`rps`): thắng 2 ván trước, 10 giây mỗi ván, lựa chọn giấu đến khi lật, hết giờ chọn ngẫu nhiên.
  **Chơi ở mọi nơi**: `/oantuti` (`/kbb`).
- **Cờ Caro** (`tictactoe`): 3x3, X đi trước (ngẫu nhiên), 10 giây mỗi nước, hết giờ đánh ô ngẫu nhiên. Chỉ trong sòng.

### Khác
- Lệnh: `/dice` / `/kbb` (sảnh), `/dice <tiền> <tien|gem>` (kèo mở), `/dice <người> <tiền> <tien|gem>` (thách đấu).
  Số tiền nhận `10000`, `10k`, `2m`.
- `/casino tieptuc` vào lại trận PvP đang đấu.
- Menu `/casino`: thêm Oẳn Tù Tì (ô 22), Cờ Caro (ô 23), Xúc Xắc (ô 28).

## 0.2.0 - 2026-10-23

### Game mới
- **Leo Thang** (`stairs`): 6 hàng x 6 ô theo texture `stairs_gui`, TNT mỗi hàng 1-1-1-2-2-3 đặt thật đầu ván.
  Hệ số x1,14 / x1,36 / x1,64 / x2,46 / x3,69 / x7,38 (RTP 95%), rê chuột lên mũi tên bên trái để xem.
  Nút rút tiền sau mỗi hàng; đóng bảng giữa ván tự rút (chưa leo hàng nào thì hoàn tiền).
- **Vòng Quay** (`roulette`): vòng 16 ô theo texture `roulette_gui`, 3 độ khó (ô thắng x2,4 / x4,8 / x10),
  3 ô thưởng (x1 / x1,5 / x0,6). Mọi độ khó RTP 95%. Kết quả bốc trước rồi mới quay; đóng bảng vẫn trả kết quả.
  Thay thế roulette cũ của server.
- **Đua Ngựa** (`races`): **một cuộc đua chung cho cả server**, texture `races_gui`.
  Người đầu tiên cược thì đếm ngược 30 giây, hết giờ thì đua 10 giây, xem kết quả 5 giây. Không ai cược thì đứng yên.
  Mỗi người cược 1 ngựa mỗi vòng, huỷ được (chuột phải) khi chưa xuất phát. Ngựa thắng x3 (RTP 75%).
  Ngựa thắng được bốc ngẫu nhiên đều lúc xuất phát; hoạt cảnh luôn cho ngựa đó về đích đầu tiên.
  Đóng bảng hay thoát game vẫn được trả thưởng. Tắt/reload plugin: đua dở thì trả kết quả, đang đếm ngược thì hoàn tiền.

### Khác
- Menu `/casino` có thêm 3 game (ô 10, 13, 15 theo texture `main_gui`).
- Quyền `boxcasino.game.stairs`, `boxcasino.game.roulette`, `boxcasino.game.races` (mặc định có).
- Test: RTP mô phỏng 2 triệu ván cho cả 5 game; kiểm tra ngựa đã bốc luôn về nhất trong 5.000 cuộc đua.

## 0.1.0 - 2026-10-23

Bản đầu tiên (mốc 0.1.0 của spec).

### Lõi
- Cược bằng Tiền (Vault) hoặc Gem (BoxCore `GemService`), chọn mỗi ván bằng nút tiền tệ trong GUI.
- Chip chỉ là hình (model `chip_1..10` của pack). Nút cược: trái +, phải -, Shift x10; chuột phải nút tiền tệ đổi mệnh giá bước.
  Mức cược, tiền tệ và mệnh giá được nhớ theo người chơi cho tới khi server tắt.
- Làm tròn tiền thưởng: phần lẻ từ 0,5 trở lên làm tròn lên.
- Ledger SQLite (`data.db`): ghi cược trước khi trừ tiền, ghi tiền thưởng trước khi trả; tự hoàn tiền / trả nốt khi khởi động lại.
- Hàng chờ trả thưởng khi không cộng được tiền (gem lúc offline).
- GUI nhận diện bằng `InventoryHolder`; huỷ mọi click/kéo khi GUI casino đang mở; mỗi người 1 click / 4 tick.
- Vùng sòng WorldGuard (`access.regions`), lệnh `/casino open` cho NPC ZNPCsPlus.
- Một tài khoản mỗi IP (IP băm SHA-256 + salt), tài khoản đầu tiên vào sòng là tài khoản chính; lệnh admin `alt`.
- Giới hạn giờ chơi (chỉ tính lúc mở GUI, mặc định 3 giờ chơi / 1 giờ nghỉ, tắt được), giới hạn lỗ mỗi ngày, tự khóa.
- Menu chính (texture `main_gui`), thống kê cá nhân, màn hình tự khóa.
- Lệnh admin: reload, toggle, stats (lời lỗ của sòng theo ngày), simulate (đo RTP), log, refund, alt, unban.
- PlaceholderAPI: won/lost/net/biggest_win/rounds/loss_left theo tiền tệ, playtime_left, rest_left.

### Game
- **Dò Mìn**: 28 ô, 3 độ khó (3 / 7 / 14 mìn), mìn đặt thật đầu ván, hệ số = RTP 97% x xác suất sống sót.
- **Kim Tự Tháp (Plinko)**: 5 phễu x0,4 / x0,8 / x3 / x0,8 / x0,4, tỉ lệ 22,5 / 20 / 15 / 20 / 22,5% (RTP 95%).

### Chưa có (các bản sau)
- 0.2.0: Leo Thang, Vòng Quay, Đua Ngựa. 0.3.0: Xúc Xắc, Oẳn Tù Tì, Cờ Caro. 0.4.0: Xì Dách.
- 0.5.0: jackpot, thông báo thắng lớn, bảng xếp hạng, máy chơi đặt trong world.
- `/casino tieptuc` chưa có tác dụng (chỉ cần cho game nhiều người).
