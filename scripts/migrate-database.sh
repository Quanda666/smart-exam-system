#!/bin/bash
# Database Migration Script for Smart Exam System
# This script helps initialize or migrate your MySQL database

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}Smart Exam System - Database Migration${NC}"
echo -e "${GREEN}========================================${NC}"
echo

# Check if .env file exists
if [ -f .env ]; then
    echo -e "${GREEN}✓ Loading configuration from .env file${NC}"
    export $(cat .env | grep -v '^#' | xargs)
else
    echo -e "${YELLOW}⚠ No .env file found, using default values${NC}"
fi

# Parse database connection details from MYSQL_URL
# Extract host, port, and database name
if [ -z "$MYSQL_URL" ]; then
    echo -e "${RED}✗ MYSQL_URL not set. Please set it in .env file${NC}"
    exit 1
fi

# Extract database details
DB_HOST=$(echo $MYSQL_URL | sed -n 's/.*:\/\/\([^:]*\).*/\1/p')
DB_PORT=$(echo $MYSQL_URL | sed -n 's/.*:\([0-9]\{4,5\}\)\/.*/\1/p')
DB_NAME=$(echo $MYSQL_URL | sed -n 's/.*\/\([^?]*\).*/\1/p')

# Use environment variables or defaults
DB_USER=${MYSQL_USERNAME:-root}
DB_PASS=${MYSQL_PASSWORD:-root}

echo -e "${GREEN}Database Configuration:${NC}"
echo "  Host: $DB_HOST"
echo "  Port: ${DB_PORT:-3306}"
echo "  Database: $DB_NAME"
echo "  User: $DB_USER"
echo

# Check if MySQL client is installed
if ! command -v mysql &> /dev/null; then
    echo -e "${RED}✗ MySQL client not found. Please install MySQL client first.${NC}"
    echo "  Ubuntu/Debian: sudo apt-get install mysql-client"
    echo "  macOS: brew install mysql-client"
    echo "  Windows: Download from https://dev.mysql.com/downloads/mysql/"
    exit 1
fi

# Test database connection
echo -e "${YELLOW}Testing database connection...${NC}"
if mysql -h"$DB_HOST" -P"${DB_PORT:-3306}" -u"$DB_USER" -p"$DB_PASS" -e "SELECT 1" &> /dev/null; then
    echo -e "${GREEN}✓ Database connection successful${NC}"
else
    echo -e "${RED}✗ Failed to connect to database${NC}"
    echo "  Please check your database credentials and ensure the database server is running"
    exit 1
fi

# Check if database exists
echo
echo -e "${YELLOW}Checking if database exists...${NC}"
if mysql -h"$DB_HOST" -P"${DB_PORT:-3306}" -u"$DB_USER" -p"$DB_PASS" -e "USE $DB_NAME" &> /dev/null; then
    echo -e "${GREEN}✓ Database '$DB_NAME' exists${NC}"
else
    echo -e "${YELLOW}⚠ Database '$DB_NAME' does not exist. Creating...${NC}"
    mysql -h"$DB_HOST" -P"${DB_PORT:-3306}" -u"$DB_USER" -p"$DB_PASS" -e "CREATE DATABASE IF NOT EXISTS $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    echo -e "${GREEN}✓ Database '$DB_NAME' created${NC}"
fi

# Run schema migration
echo
echo -e "${YELLOW}Running schema migration...${NC}"
if [ -f backend/src/main/resources/db/schema.sql ]; then
    mysql -h"$DB_HOST" -P"${DB_PORT:-3306}" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < backend/src/main/resources/db/schema.sql
    echo -e "${GREEN}✓ Schema migration completed${NC}"
else
    echo -e "${RED}✗ Schema file not found: backend/src/main/resources/db/schema.sql${NC}"
    exit 1
fi

# Run data seeding
echo
echo -e "${YELLOW}Running data seeding...${NC}"
if [ -f backend/src/main/resources/db/data.sql ]; then
    mysql -h"$DB_HOST" -P"${DB_PORT:-3306}" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < backend/src/main/resources/db/data.sql
    echo -e "${GREEN}✓ Data seeding completed${NC}"
else
    echo -e "${YELLOW}⚠ Data file not found: backend/src/main/resources/db/data.sql${NC}"
fi

# Verify tables
echo
echo -e "${YELLOW}Verifying database tables...${NC}"
TABLE_COUNT=$(mysql -h"$DB_HOST" -P"${DB_PORT:-3306}" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -sN -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$DB_NAME';")
echo -e "${GREEN}✓ Found $TABLE_COUNT tables in database${NC}"

# Show table list
echo
echo -e "${GREEN}Database tables:${NC}"
mysql -h"$DB_HOST" -P"${DB_PORT:-3306}" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "SHOW TABLES;"

echo
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✓ Database migration completed successfully!${NC}"
echo -e "${GREEN}========================================${NC}"
echo
echo -e "${YELLOW}Default admin account:${NC}"
echo "  Username: admin"
echo "  Password: admin123"
echo
echo -e "${YELLOW}Important: Please change the admin password after first login!${NC}"
