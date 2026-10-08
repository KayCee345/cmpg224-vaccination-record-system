INSERT INTO users VALUES (1, 'admin', 'ADMIN'), (2, 'nurse', 'WORKER'), (3, 'thandi', 'PATIENT'), (4, 'sipho', 'PATIENT');
INSERT INTO patients VALUES
  (10, 3, 'Thandi Mokoena', '2601105000081', DATE '2026-01-10', 'Female'),
  (11, 4, 'Sipho Dlamini',  '9003015000087', DATE '1990-03-01', 'Male');
INSERT INTO vaccines VALUES (1, 'MMR'), (2, 'Polio');
INSERT INTO vaccination_records VALUES
  (100, 10, 1, 2, 1, 'B-MMR-1',  DATE '2026-10-05'),
  (101, 11, 1, 2, 1, 'B-MMR-1',  DATE '2026-10-05'),
  (102, 11, 2, 2, 2, 'B-POL-7',  DATE '2026-10-04');
INSERT INTO vaccine_batches VALUES
  (1, 1, 'B-MMR-1', 40, 'Maker A', DATE '2026-01-01', DATE '2027-06-30'),
  (2, 2, 'B-POL-7',  5, 'Maker B', NULL,              DATE '2026-11-01');
