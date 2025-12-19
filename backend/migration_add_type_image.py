import sqlite3

def migrate():
    print("Migrating database...")
    con = sqlite3.connect("database.db")
    cur = con.cursor()
    
    # Check if columns exist
    try:
        cur.execute("SELECT is_fixed_image FROM meetingtype LIMIT 1")
    except sqlite3.OperationalError:
        print("Adding is_fixed_image column...")
        cur.execute("ALTER TABLE meetingtype ADD COLUMN is_fixed_image BOOLEAN DEFAULT 0")

    try:
        cur.execute("SELECT cover_image FROM meetingtype LIMIT 1")
    except sqlite3.OperationalError:
        print("Adding cover_image column...")
        cur.execute("ALTER TABLE meetingtype ADD COLUMN cover_image VARCHAR")

    con.commit()
    con.close()
    print("Migration complete.")

if __name__ == "__main__":
    migrate()
