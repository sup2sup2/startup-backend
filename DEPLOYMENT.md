# 배포 환경변수

백엔드를 다시 배포하기 전에 아래 환경변수를 설정합니다.

- `DB_URL`: MySQL JDBC URL
- `DB_USERNAME`: DB 사용자
- `DB_PASSWORD`: DB 비밀번호
- `SEOUL_API_KEY`: 서울 열린데이터광장 API 키
- `AWS_ACCESS_KEY`: S3 접근 키
- `AWS_SECRET_KEY`: S3 비밀 키
- `AUTH_TOKEN_SECRET`: 32자 이상의 충분히 긴 무작위 문자열
- `CORS_ALLOWED_ORIGINS`: 허용할 프론트엔드 주소. 여러 개면 쉼표로 구분
- `JPA_DDL_AUTO`: 운영 환경에서는 `validate` 권장

부하 분산 기능은 다음 환경변수를 설정할 때 활성화됩니다.

- `REDIS_ENABLED=true`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME`, `REDIS_PASSWORD`, `REDIS_SSL_ENABLED`
- `DB_REPLICA_URL`, `DB_REPLICA_USERNAME`, `DB_REPLICA_PASSWORD`: 읽기 전용 MySQL 사용자 권장
- `DB_POOL_MAX_SIZE`, `DB_REPLICA_POOL_MAX_SIZE`: 무료 서버에서는 합계가 DB 연결 한도를 넘지 않도록 설정

Redis 또는 Replica 환경변수가 없으면 기존처럼 단일 MySQL만 사용합니다. Redis 장애 시 원본 조회로 전환하고 Replica 장애 시 Primary에서 조회를 한 번 재시도합니다.

이미 Git에 포함됐던 DB 비밀번호와 서울시 API 키는 배포 전에 새 값으로 교체합니다.

프론트엔드에는 `NEXT_PUBLIC_API_URL`과 `NEXT_PUBLIC_KAKAO_MAP_KEY`를 설정합니다.
카카오 개발자 콘솔에서 실제 프론트엔드 도메인만 JavaScript 키의 허용 도메인으로 등록합니다.

## Render 무료 배포

저장소 루트의 `render.yaml`을 Blueprint로 가져오면 무료 Docker Web Service가 생성됩니다.
비밀값은 Blueprint 생성 화면에서 직접 입력하며 저장소에는 커밋하지 않습니다.

Aiven MySQL을 사용할 때 `DB_URL` 형식은 다음과 같습니다.

```text
jdbc:mysql://호스트:포트/defaultdb?sslMode=REQUIRED
```

Render 주소가 생성되면 프론트엔드 Vercel 환경변수 `NEXT_PUBLIC_API_URL`을 해당 주소로 변경하고 재배포합니다.
