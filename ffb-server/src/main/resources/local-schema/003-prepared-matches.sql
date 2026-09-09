CREATE TABLE ffb_prepared_matches (
  match_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  document_version INT NOT NULL,
  document_json MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  CHECK (document_version BETWEEN 1 AND 2147483646),
  CHECK (OCTET_LENGTH(document_json) <= 65536)
) ENGINE=InnoDB
