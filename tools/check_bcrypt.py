import bcrypt
hash_value = open('rbac-bootstrap/src/main/resources/db/migration/V220__seed_builtin_data.sql').read()
import re
m = re.search(r"'\$2a\$10\$[^']+'", hash_value)
h = m.group(0).strip("'")
print("stored hash:", h)
print("Admin@123  ->", bcrypt.checkpw(b"Admin@123", h.encode()))
print("admin123   ->", bcrypt.checkpw(b"admin123", h.encode()))
print("Admin123   ->", bcrypt.checkpw(b"Admin123", h.encode()))
print("admin@123  ->", bcrypt.checkpw(b"admin@123", h.encode()))
print("RBACadmin123 ->", bcrypt.checkpw(b"RBACadmin123", h.encode()))
# 再生成几个备选让用户挑
for pwd in [b'Admin@123!', b'Rbac2026!', b'Admin@2026']:
    print(pwd.decode(), "->", bcrypt.hashpw(pwd, bcrypt.gensalt(10)).decode())
