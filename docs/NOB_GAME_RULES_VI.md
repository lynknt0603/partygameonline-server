# NIGHT OF BLOODLINES - HƯỚNG DẪN CHƠI

> **Bản đặc tả gameplay của Night of Bloodlines.** Toàn bộ câu chữ trong tài liệu này được viết mới cho dự án. Khi công khai, chỉ sử dụng tên gọi, hình ảnh, biểu tượng, bố cục và nội dung do dự án tự tạo; không sao chép hoặc dịch sát rulebook, card text, artwork hay nhận diện của trò chơi khác.

## 1. Tổng quan

**Night of Bloodlines** là game suy luận ẩn phe theo vòng dành cho **4-11 người chơi**.

Mỗi vòng, người chơi bí mật thuộc một trong ba Bloodline:

- **Vampires** - Ma Cà Rồng
- **Werewolves** - Ma Sói
- **Halfblood** - Huyết Lai, chỉ xuất hiện khi số người chơi là số lẻ

Người chơi không biết ai là đồng minh, ai là đối thủ. Trong Đêm, mỗi người dùng các Role Card để thu thập thông tin, đánh tráo Bloodline, thao túng Moon Mark hoặc loại người khác.

Mục tiêu cuối cùng là đạt **10 điểm Moon Mark**.

---

## 2. Thành phần gameplay

### Bloodline Cards

- Vampires: rank 1-5
- Werewolves: rank 1-5
- Halfblood: 1 lá, không có rank

**Rank 1 mạnh nhất**, sau đó là 2, 3, 4, 5.

### 30 Role Cards có số

Mỗi role có 6 lá, đánh số 1-6:

1. Shadow Stalker
2. Blood Seer
3. Shapeshifter
4. Feral Killer
5. Hunter

Số trên card là **thứ tự ưu tiên xử lý trong cùng phase**: 1 trước 2, ... 6 cuối.

### 3 Special Cards

- **Veil Reversal**
- **Last Offering**
- **Last Hope**

Ba lá này không mang số và có timing riêng.

### Moon Marks

Moon Mark là token điểm bí mật có giá trị **2, 3 hoặc 4 điểm**.

- Chủ sở hữu được xem giá trị token của mình.
- Người khác chỉ thấy **số lượng Moon Mark**, không thấy giá trị.

---

## 3. Chuẩn bị theo số người chơi

Với `N` người chơi:

- Dùng `floor(N/2)` Vampire, bắt đầu từ rank 1.
- Dùng cùng số lượng Werewolf, bắt đầu từ rank 1.
- Nếu `N` lẻ, thêm 1 Halfblood.

| Người chơi | Bloodline sử dụng |
|---|---|
| 4 | Vampire 1-2, Werewolf 1-2 |
| 5 | Vampire 1-2, Werewolf 1-2, Halfblood |
| 6 | Vampire 1-3, Werewolf 1-3 |
| 7 | Vampire 1-3, Werewolf 1-3, Halfblood |
| 8 | Vampire 1-4, Werewolf 1-4 |
| 9 | Vampire 1-4, Werewolf 1-4, Halfblood |
| 10 | Vampire 1-5, Werewolf 1-5 |
| 11 | Vampire 1-5, Werewolf 1-5, Halfblood |

Mỗi vòng đều **xáo và chia lại Bloodline từ đầu**.

---

## 4. Cấu trúc một vòng

1. **Bloodline Assignment**
2. **Role Draft**
3. **The Night**
   1. Shadow Stalker
   2. Blood Seer
   3. Shapeshifter
   4. Feral Killer
   5. Hunter
4. **Bloodline Reveal**
5. **Scoring**
6. **Victory Check**

---

## 5. Bloodline Assignment

Server xáo bộ Bloodline phù hợp với số người và chia mỗi người 1 lá bí mật.

- Người chơi chỉ được xem Bloodline mà họ **đang biết**.
- Người chơi có thể nói thật, nói dối, suy luận và thuyết phục người khác qua chat/voice.
- Nếu Shapeshifter #1 đổi Bloodline của một người, người đó **không tự động biết Bloodline mới**.

---

## 6. Role Draft

1. Xáo toàn bộ 33 Role/Special Cards.
2. Chia **3 lá** cho mỗi người.
3. Mỗi người bí mật chọn 1 lá để giữ.
4. Hai lá còn lại được chuyển cho người bên trái.
5. Từ 2 lá vừa nhận, mỗi người chọn thêm 1 lá để giữ.
6. Lá còn lại được bỏ úp vào **Discard Pile**.
7. Card không được chia cũng được đặt sang một bên và giữ bí mật.

Kết thúc draft, bình thường mỗi người có **2 card**.

---

## 7. Luật chung của The Night

### Thứ tự phase

`Shadow Stalker -> Blood Seer -> Shapeshifter -> Feral Killer -> Hunter`

### Reveal/Pass

Khi đến một phase:

1. Người còn sống có card thuộc phase đó bí mật chọn card muốn **Reveal** hoặc **Pass**.
2. Lựa chọn chỉ được công khai sau khi cửa sổ submit đóng.
3. Card đã reveal được đưa vào hàng đợi theo số tăng dần 1 -> 6.
4. Khi tới lượt một card, người sở hữu mới chọn target/option dựa trên trạng thái hiện tại.
5. Nếu owner đã bị loại **trước khi card của họ được resolve**, card đó bị hủy.

Người chơi có thể giữ card không dùng để bluff. Card đã bỏ qua phase của nó không được dùng lại trong vòng, trừ khi một effect cho phép.

### Khi bị loại

Người bị loại:

- không được thực hiện game action mới;
- card chưa reveal không còn dùng được;
- Bloodline vẫn bí mật nếu chưa có effect công khai nó;
- vẫn được chat/nói chuyện nếu room cho phép;
- vẫn có thể nhận Moon Mark nếu Bloodline cuối vòng của họ thuộc phe thắng.

---

# 8. Các Role

## Shadow Stalker #1-#6

**Ability:** chọn 1 người khác và bí mật xem Bloodline hiện tại của họ.

- Kết quả chỉ gửi cho Shadow Stalker.
- Actor có thể nói thật hoặc nói dối về thông tin này.
- #1-#6 cùng effect; số chỉ quyết định thứ tự resolve.

---

## Blood Seer #1-#6

**Ability:** chọn 1 người khác và bí mật xem:

1. Bloodline hiện tại của target; và
2. 1 Role/Special Card chưa reveal của target, do server chọn ngẫu nhiên.

Nếu target chỉ còn 1 card chưa reveal thì xem card đó; nếu không còn card nào thì chỉ xem Bloodline.

#1-#6 cùng effect; số chỉ quyết định thứ tự resolve.

---

## Shapeshifter #1 - Bloodline Exchange

1. Chọn 2 người khác nhau; có thể chọn chính mình là một trong hai.
2. Bí mật xem Bloodline hiện tại của cả hai.
3. Chọn **Swap** hoặc **Keep**.
4. Nếu Swap, server đổi Bloodline của hai người.
5. Hai người bị đổi không tự động được xem Bloodline mới.
6. Scoring cuối vòng dùng **Bloodline sau cùng**, không dùng Bloodline ban đầu.

---

## Shapeshifter #2 - Echoes of the Fallen

1. Server lấy ngẫu nhiên tối đa 2 card từ Discard Pile cho actor xem.
2. Actor chọn 1 card.
3. Chọn:
   - **Play Now** nếu card có thể resolve hợp lệ ngay; hoặc
   - **Keep for Later** để thêm card vào hand.
4. Card còn lại quay về Discard Pile.

Card được lấy lại có thể được dùng ngoài phase thông thường nếu chọn Play Now và effect đó có thể resolve ngay. Special dạng Reaction/Final Reveal không có trigger hiện tại thì chỉ được giữ lại.

---

## Shapeshifter #3 - Unmask

1. Chọn 1 người khác.
2. Bí mật xem Bloodline hiện tại của target.
3. Chọn giữ bí mật hoặc **Reveal Public** cho toàn room.

---

## Shapeshifter #4 - Moon Broker

1. Chọn 1 target.
2. Chọn xem **một trong hai**:
   - Bloodline hiện tại của target; hoặc
   - giá trị 1 Moon Mark ngẫu nhiên của target.
3. Nếu cả actor và target đều có Moon Mark, actor có thể đổi 1 token của mình lấy 1 token đang úp của target.
4. Token lấy từ target không bắt buộc là token vừa xem.

---

## Shapeshifter #5 - Moon Thief

1. Công khai Bloodline hiện tại của chính mình.
2. Chọn 1 người có **nhiều Moon Mark token hơn mình về số lượng**.
3. Nếu target có 1 Moon Mark thì chuyển trực tiếp. Nếu target có từ 2 Moon Mark trở lên, actor được chọn 1 trong toàn bộ các Moon Mark đang úp của target (2/3/4... lá tùy số lượng). Chỉ Moon Mark đã chọn mới được chuyển sang actor và lộ giá trị sau khi chuyển.
4. Không kiểm tra thắng game ngay; Victory Check vẫn chỉ diễn ra cuối vòng.

---

## Shapeshifter #6 - Final Judgement

1. Công khai Bloodline hiện tại của chính mình.
2. Chọn 1 người đang sống khác.
3. Target bị loại ngay.
4. **Veil Reversal và Last Offering không thể phản ứng với Final Judgement.**

---

## Feral Killer #1-#6

**Ability:** chọn và cố gắng loại 1 người đang sống khác mà **không được xem Bloodline trước**.

1. Chọn target.
2. Nếu target có Special hợp lệ, mở Reaction Window.
3. Sau reaction, server quyết định ai bị loại.

#1-#6 cùng effect; số quyết định thứ tự resolve.

---

## Hunter #1-#6

**Ability:** kiểm tra trước, sau đó mới quyết định.

1. Chọn 1 người đang sống khác.
2. Bí mật xem Bloodline hiện tại của target.
3. Chọn:
   - **Spare** - không làm gì thêm;
   - **Eliminate** - thử loại target và mở Reaction Window nếu cần.

#1-#6 cùng effect; số quyết định thứ tự resolve.

---

# 9. Special Cards

## Veil Reversal

**Reaction** - chỉ dùng khi holder đang sống bị **Feral Killer hoặc Hunter** chọn để loại.

Nếu Reveal:

- holder sống;
- attacker bị loại thay;
- kill phản xạ không tạo thêm reaction chain.

Không dùng được với Final Judgement.

---

## Last Offering

**Reaction** - chỉ dùng khi holder đang sống bị **Feral Killer hoặc Hunter** chọn để loại.

Nếu Reveal:

1. Holder nhận ngay 1 Moon Mark ngẫu nhiên.
2. Holder vẫn bị loại.
3. Attacker không bị ảnh hưởng.

Không dùng được với Final Judgement.

Nếu holder có cả Veil Reversal và Last Offering thì trong cùng một kill event chỉ được chọn **một** trong: Veil Reversal / Last Offering / Decline.

---

## Last Hope

**Final Reveal**.

Tại Bloodline Reveal:

- Holder đã chết -> không có effect.
- Holder còn sống -> card tự động reveal.

Nếu holder là **Vampire hoặc Werewolf**, Bloodline hiện tại của holder thắng vòng bất kể rank.

Nếu holder là **Halfblood**:

- Vampire và Werewolf đều không thắng vòng;
- Halfblood nhận 1 Moon Mark vì sống sót;
- không người nào khác nhận Moon Mark từ scoring của vòng đó.

---

# 10. Bloodline Reveal & Scoring

### Reveal

Sau Hunter phase:

- Bloodline của người còn sống được công khai.
- Bloodline của người đã bị loại không bắt buộc công khai.
- Last Hope được xử lý trước so sánh rank thông thường.

### So sánh Vampire và Werewolf

Lấy rank của survivor mỗi phe, sắp xếp tăng dần và so sánh từng vị trí.

- Rank nhỏ hơn thắng.
- Nếu rank đầu bằng nhau, so rank tiếp theo.
- Nếu hai dãy survivor giống hệt nhau -> **Total Tie**.
- Nếu các rank đã so đều bằng nhưng một bên còn survivor tiếp theo còn bên kia hết, bên có survivor tiếp theo thắng.

Ví dụ:

- Vampire `[1]`, Werewolf `[2,3,4]` -> Vampire thắng.
- Vampire `[1,3]`, Werewolf `[1,4]` -> rank 1 hòa, 3 thắng 4 -> Vampire thắng.

### Khi một phe chính thắng

- Mỗi người có **Bloodline cuối vòng** thuộc phe thắng nhận 1 Moon Mark, **kể cả đã bị loại**.
- Halfblood nếu còn sống cũng nhận 1 Moon Mark.

### Total Tie

Nếu hai phe chính hòa hoàn toàn:

- không phe chính nào thắng;
- **mọi người còn sống** nhận đúng 1 Moon Mark;
- Halfblood không được cộng thêm token lần hai.

### Halfblood

Trong vòng thông thường, Halfblood chỉ cần **sống tới scoring** để nhận Moon Mark; không cần một phe chính cụ thể thắng.

---

# 11. Kết thúc game

Victory Check chỉ chạy **sau toàn bộ scoring của vòng**.

1. Tính tổng giá trị Moon Mark của từng người.
2. Nếu chưa ai đạt 10 -> bắt đầu vòng mới.
3. Nếu có người đạt >=10:
   - tổng điểm cao nhất thắng;
   - nếu nhiều người cùng điểm cao nhất, họ đồng chiến thắng.
4. Khi game kết thúc, server có thể công khai toàn bộ tổng điểm/token trong màn kết quả.

---

# 12. Quyền xem thông tin trên web

### Public

- player/seat;
- alive/dead;
- số lượng Moon Mark;
- Role Card đã reveal;
- Bloodline đã được effect công khai;
- Bloodline survivor ở Final Reveal;
- public action log;
- phase, countdown và kết quả vòng.

### Private

- hand của chính mình;
- Bloodline của mình nếu hiện tại mình được quyền biết;
- giá trị Moon Mark của mình;
- kết quả điều tra riêng;
- card discard mà Shapeshifter #2 đang xem;
- pending decision của chính mình.

**Backend là nguồn sự thật duy nhất. Frontend không tự tính kết quả gameplay.**

---

# 13. Timeout online - mặc định đề xuất

- Draft: 30 giây
- Reveal/Pass: 20 giây
- Target/decision: 25 giây
- Reaction: 10 giây

Khi timeout:

- Draft -> random một lựa chọn hợp lệ.
- Reveal/Pass -> Pass.
- Hunter -> Spare.
- Reaction -> Decline.
- Optional Shapeshifter decision -> chọn phương án an toàn không gây action ngoài ý muốn.

Các mốc này phải cấu hình được theo room/server.

---

# 14. Checklist tài sản sáng tạo khi public

Nên chỉ dùng các yếu tố do dự án tự tạo:

- Night of Bloodlines;
- Vampires / Werewolves / Halfblood;
- Shadow Stalker / Blood Seer / Shapeshifter / Feral Killer / Hunter;
- Veil Reversal / Last Offering / Last Hope;
- Moon Marks;
- artwork, icon, frame, lore, UI và wording riêng.

Không đưa vào public build:

- scan/artwork/logo của game khác;
- rule text sao chép hoặc dịch sát;
- tên role/faction mang tính nhận diện đặc thù của sản phẩm khác;
- layout/iconography sao chép có chủ ý;
- nội dung SEO khiến người dùng hiểu nhầm đây là sản phẩm chính thức/được cấp phép của bên thứ ba.

