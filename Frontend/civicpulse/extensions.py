"""
Shared extension instances.

Kept in their own module (rather than declared in app.py) so models.py and
auth.py can import `db` / `login_manager` without circular imports.
"""

from flask_sqlalchemy import SQLAlchemy
from flask_login import LoginManager

db = SQLAlchemy()
login_manager = LoginManager()
