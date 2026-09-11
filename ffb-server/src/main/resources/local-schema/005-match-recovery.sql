CREATE TABLE ffb_match_recovery (
  matchid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  generation BIGINT NOT NULL,
  artifact_json LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  CHECK (generation > 0),
  CHECK (OCTET_LENGTH(artifact_json) <= 33554432)
) ENGINE=InnoDB
