-- Insert
INSERT INTO fused_geometries (subject_A, subject_B, geom) 
SELECT links.nodea, links.nodeb, ST_Translate(b_g, a_x-b_x,a_y-b_y)
FROM links 
INNER JOIN (SELECT dataset_a_geometries.subject AS a_s,
		   dataset_b_geometries.subject AS b_s,
		   dataset_a_geometries.geom AS a_g,
		   dataset_b_geometries.geom AS b_g,
		   ST_X(dataset_a_geometries.geom) AS a_x,
		   ST_Y(dataset_a_geometries.geom) AS a_y,
		   ST_X(ST_Centroid(dataset_b_geometries.geom)) AS b_x,
		   ST_Y(ST_Centroid(dataset_b_geometries.geom)) AS b_y
		FROM dataset_a_geometries, dataset_b_geometries) AS geoms 
		ON(links.nodea = geoms.a_s AND links.nodeb = geoms.b_s)
-- Verify

SELECT links.nodea, links.nodeb, St_asText(ST_Translate(b_g, a_x-b_x,a_y-b_y)) AS ge
FROM links 
INNER JOIN (SELECT dataset_a_geometries.subject AS a_s, dataset_b_geometries.subject AS b_s,
		dataset_a_geometries.geom AS a_g, dataset_b_geometries.geom AS b_g,
		ST_X(dataset_a_geometries.geom) AS a_x, ST_Y(dataset_a_geometries.geom) AS a_y,
		ST_X(ST_Centroid(dataset_b_geometries.geom)) AS b_x, ST_Y(ST_Centroid(dataset_b_geometries.geom)) AS b_y
		FROM dataset_a_geometries, dataset_b_geometries) AS geoms ON(links.nodea = geoms.a_s AND links.nodeb = geoms.b_s)