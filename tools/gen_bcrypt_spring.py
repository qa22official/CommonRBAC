import bcrypt
h = bcrypt.hashpw(b"Admin@123", bcrypt.gensalt(10)).decode()
# Spring Security BCryptPasswordEncoder 生成 $2a$ 前缀的 hash（bcrypt 库默认 $2b$，Spring 会转成 $2a$）
# 所以直接用 bcrypt 库生成后，把 $2b 换成 $2a 即可
h_spring = h.replace("$2b$", "$2a$")
print("Admin@123 ->", h_spring)
print("verify Admin@123:", bcrypt.checkpw(b"Admin@123", h_spring.encode()))
