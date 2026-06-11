# Distributed AI Flow Report

- Generated: 2026-06-11 11:19:27 +08:00
- Gateway: `http://localhost:18080`
- AI service: `http://localhost:8106`
- Summary: PASS=5, FAIL=0

| Status | Check | Detail |
| --- | --- | --- |
| PASS | gateway login | student and company tokens issued |
| PASS | DashScope configured | provider=dashscope, model=qwen-plus |
| PASS | real DashScope candidate screening | mocked=false, score=78 |
| PASS | delivery created | deliveryId=De4a46eb1 |
| PASS | RocketMQ delivery-to-screening flow | taskId=AST-5f61705e, status=COMPLETED, count=1 |
