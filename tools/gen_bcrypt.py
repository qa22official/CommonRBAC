import bcrypt
# 为默认密码 Admin@123 生成 hash（cost=10 与 PasswordPolicy 一致）
h = bcrypt.hashpw(b"Admin@123", bcrypt.gensalt(10)).decode()
print("Admin@123 ->", h)
print("verify:", bcrypt.checkpw(b"Admin@123", h.encode()))
