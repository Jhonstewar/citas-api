-- =====================================================================================
-- V7 — Direccion de HIC literal segun el PRD §3 (verificacion independiente de S3, HU-010 CA-02).
--
-- V4 escribio "Bucaramanga - Piedecuesta" con guion y espacios; el PRD usa la raya sin espacios
-- ("Bucaramanga–Piedecuesta"). La ciudad y el departamento siguen en sus propias columnas.
-- =====================================================================================

UPDATE sites
SET address = 'Km 7 Autopista Bucaramanga–Piedecuesta, Valle de Menzulí'
WHERE code = 'HIC';
