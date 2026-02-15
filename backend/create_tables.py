import sys
import os
sys.path.append(os.getcwd())
from backend.database import create_db_and_tables, engine
from backend.models import *
from sqlmodel import SQLModel

print("Creating missing tables...")
SQLModel.metadata.create_all(engine)
print("Done.")
