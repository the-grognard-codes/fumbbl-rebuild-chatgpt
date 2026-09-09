CREATE TABLE ffb_saved_teams (
  team_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  owner_subject VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  document_version INT NOT NULL,
  catalog_version VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  document_json TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  INDEX saved_team_owner (owner_subject),
  CHECK (owner_subject IN ('home','away')),
  CHECK (document_version BETWEEN 1 AND 2147483646),
  CHECK (OCTET_LENGTH(document_json) <= 16384)
) ENGINE=InnoDB
