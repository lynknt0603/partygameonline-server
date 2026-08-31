# partygameonline-server

Dịch vụ Backend cho nền tảng chơi board game và party game trực tuyến nhiều người chơi theo thời gian thực. Hệ thống được xây dựng theo kiến trúc Server-Authoritative Modular Monolith, đảm bảo toàn bộ luật chơi, ẩn giấu thông tin (vai trò ẩn, bài trên tay) và xử lý trạng thái đều được quản lý tập trung và an toàn trên server.

## Công nghệ sử dụng

- Ngôn ngữ và Framework: Java 21, Spring Boot 4.1.0 (Spring WebMVC, Spring WebSocket, Spring Security, Spring Data JPA)
- Cơ sở dữ liệu: PostgreSQL 13+, Flyway Migration
- Kiểm thử: JUnit 5, Mockito, Spring Boot Test
- Công cụ build: Maven (Maven Wrapper)

## Cấu trúc thư mục

- src/main/java/com/partygameonline/catalog: Quản lý danh mục game
- src/main/java/com/partygameonline/room: Quản lý phòng chơi, vị trí người chơi và khoá đồng thời
- src/main/java/com/partygameonline/game/core: Khung hợp đồng (contracts) và registry của game engine
- src/main/java/com/partygameonline/game/runtime: Điều phối và quản lý phiên game đang chạy
- src/main/java/com/partygameonline/game/nob: Engine và bộ chiếu trạng thái (state projector) cho game Night of Bloodlines
- src/main/java/com/partygameonline/realtime: Quản lý kết nối WebSocket, tin nhắn và kênh chat
- src/main/java/com/partygameonline/history: Lưu trữ và tra cứu lịch sử ván đấu
- src/main/java/com/partygameonline/security: Cấu hình bảo mật, phân quyền, CORS và CSRF
- src/main/java/com/partygameonline/session: Quản lý session khách (guest session)

## Yêu cầu môi trường

- JDK 21 trở lên
- PostgreSQL 13 trở lên

## Thiết lập cơ sở dữ liệu

Tạo các database cần thiết trên PostgreSQL:

```sql
CREATE DATABASE partygameonline;
CREATE DATABASE partygameonline_test;
```

Cấu hình kết nối cơ sở dữ liệu trong file `src/main/resources/application.properties` hoặc file cấu hình môi trường tương ứng.

## Hướng dẫn build và chạy testcase

### 1. Chạy toàn bộ testcase

Sử dụng Maven Wrapper có sẵn trong dự án:

- Trên Linux / macOS:
```bash
./mvnw clean test
```

- Trên Windows:
```cmd
mvnw.cmd clean test
```

### 2. Chạy testcase cụ thể

Chạy một class kiểm thử xác định:
```bash
./mvnw test -Dtest=PartyGameOnlineApplicationTests
```

Chạy một phương thức kiểm thử cụ thể trong class:
```bash
./mvnw test -Dtest=PartyGameOnlineApplicationTests#contextLoads
```

### 3. Đóng gói ứng dụng (Build Package)

- Đóng gói kèm chạy kiểm thử:
```bash
./mvnw clean package
```

- Đóng gói bỏ qua bước kiểm thử (nhanh):
```bash
./mvnw clean package -DskipTests
```

File thực thi `.jar` sẽ được tạo trong thư mục `target/`.

## Hướng dẫn chạy ứng dụng

### 1. Chạy trực tiếp qua Maven

- Chạy với cấu hình mặc định:
```bash
./mvnw spring-boot:run
```

- Chạy với profile `dev`:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 2. Chạy bằng script hỗ trợ

- Trên macOS / Linux:
```bash
./run.sh
```

- Trên Windows (PowerShell):
```powershell
.\run.ps1
```

Hệ thống cung cấp các endpoint kiểm tra trạng thái hoạt động:
- Health check: `GET http://localhost:8080/actuator/health`
- Info: `GET http://localhost:8080/actuator/info`

## Các API chính

| Phương thức | Đường dẫn | Mô tả |
|---|---|---|
| GET | /api/v1/csrf | Lấy CSRF token |
| POST | /api/v1/session/guest | Tạo phiên đăng nhập khách |
| GET | /api/v1/session/me | Lấy thông tin phiên và phòng hiện tại |
| GET | /api/v1/games | Danh sách game khả dụng |
| POST | /api/v1/rooms | Tạo phòng chơi mới |
| POST | /api/v1/rooms/{id}/join | Tham gia vào phòng chơi |
| POST | /api/v1/rooms/{id}/ready | Chuyển đổi trạng thái sẵn sàng |
| POST | /api/v1/rooms/{id}/start | Bắt đầu ván chơi (chủ phòng) |
| WS | /ws | Kết nối WebSocket thời gian thực |
| GET | /api/v1/matches | Lịch sử các ván đấu đã kết thúc |

## Tài liệu chi tiết

- Kiến trúc hệ thống: `docs/BACKEND-ARCHITECTURE.md`
- Thiết kế Game Engine: `docs/GAME-ENGINE.md`
- Thiết kế Cơ sở dữ liệu: `docs/DATABASE.md`
- Đặc tả REST API: `docs/REST-API.md`
- Giao thức WebSocket: `docs/WEBSOCKET-PROTOCOL.md`
- Luật chơi Night of Bloodlines: `docs/NOB_GAME_RULES_VI.md`

## Giấy phép

All rights reserved.
