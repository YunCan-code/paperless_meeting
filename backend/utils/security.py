import bcrypt


def hash_password(plain_password: str) -> str:
    """对明文密码进行 bcrypt 哈希"""
    return bcrypt.hashpw(plain_password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """验证明文密码是否匹配哈希值，兼容未哈希的旧密码"""
    if not hashed_password:
        return False
    # 兼容旧的明文密码（不以 $2b$ 开头的视为明文）
    if not hashed_password.startswith("$2b$"):
        return plain_password == hashed_password
    return bcrypt.checkpw(plain_password.encode("utf-8"), hashed_password.encode("utf-8"))
