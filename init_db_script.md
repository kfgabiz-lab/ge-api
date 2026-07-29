INSERT INTO public.code_group
(id, is_active, created_at, created_by, description, group_code, group_name, updated_at, updated_by, group_name_msg_key)
VALUES(62, true, '2026-07-28 15:06:15.418', 'comlbg', NULL, 'TRAININGVFD', '교육생의 제품 이해도', '2026-07-28 15:06:15.418', 'comlbg', 'training.label.studentProductKnowledgeLevel');
INSERT INTO public.code_group
(id, is_active, created_at, created_by, description, group_code, group_name, updated_at, updated_by, group_name_msg_key)
VALUES(61, true, '2026-07-28 15:06:15.025', 'comlbg', NULL, 'TRAININGJOIN', '관련 분야', '2026-07-28 15:06:15.025', 'comlbg', 'training.label.areaOfInvolvement');
INSERT INTO public.code_group
(id, is_active, created_at, created_by, description, group_code, group_name, updated_at, updated_by, group_name_msg_key)
VALUES(60, true, '2026-07-28 15:06:14.487', 'comlbg', NULL, 'TRAININGJOBTITLE', '교육생 직위', '2026-07-28 15:06:14.487', 'comlbg', 'training.label.studentJobTitle');

INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(478, true, '06', '2026-07-28 15:06:19.361', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'Other Topics', 6, '2026-07-28 15:06:19.361', 'comlbg', 62, 'training.label.otherTopics');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(477, true, '05', '2026-07-28 15:06:19.210', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'Auto-Tuning', 5, '2026-07-28 15:06:19.210', 'comlbg', 62, 'training.label.autoTuning');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(476, true, '04', '2026-07-28 15:06:19.054', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'PID Applications', 4, '2026-07-28 15:06:19.054', 'comlbg', 62, 'training.label.pidApplications');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(475, true, '03', '2026-07-28 15:06:18.894', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'DriveView Software', 3, '2026-07-28 15:06:18.894', 'comlbg', 62, 'training.label.driveViewSoftware');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(474, true, '02', '2026-07-28 15:06:18.702', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'Drive technology (construction, theory of operation)', 2, '2026-07-28 15:06:18.702', 'comlbg', 62, 'training.label.driveTechnology');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(473, true, '01', '2026-07-28 15:06:18.519', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'Motor technology (construction, theory of operation)', 1, '2026-07-28 15:06:18.519', 'comlbg', 62, 'training.label.motorTechnology');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(472, true, '07', '2026-07-28 15:06:18.367', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'Other', 7, '2026-07-28 15:06:18.367', 'comlbg', 61, 'training.label.involvementOther');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(471, true, '06', '2026-07-28 15:06:18.214', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'Unit troubleshooting (to determine damaged components)', 6, '2026-07-28 15:06:18.214', 'comlbg', 61, 'training.label.unitTroubleshooting');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(470, true, '05', '2026-07-28 15:06:18.051', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'Serial communications', 5, '2026-07-28 15:06:18.051', 'comlbg', 61, 'training.label.serialCommunications');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(469, true, '04', '2026-07-28 15:06:17.886', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'Sales and Explanation of the Product', 4, '2026-07-28 15:06:17.886', 'comlbg', 61, 'training.label.salesExplanationProduct');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(468, true, '03', '2026-07-28 15:06:17.700', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'Field Troubleshooting (understanding fault codes)', 3, '2026-07-28 15:06:17.700', 'comlbg', 61, 'training.label.fieldTroubleshooting');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(467, true, '02', '2026-07-28 15:06:17.530', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'Integration and Parameter setup', 2, '2026-07-28 15:06:17.530', 'comlbg', 61, 'training.label.integrationParameterSetup');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(466, true, '01', '2026-07-28 15:06:17.362', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'Installation, Start-up', 1, '2026-07-28 15:06:17.362', 'comlbg', 61, 'training.label.installationStartup');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(465, true, '07', '2026-07-28 15:06:17.180', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'Other', 7, '2026-07-28 15:06:17.180', 'comlbg', 60, 'training.label.jobTitleOther');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(464, true, '06', '2026-07-28 15:06:16.986', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'Field Service Engineer', 6, '2026-07-28 15:06:16.986', 'comlbg', 60, 'training.label.fieldServiceEngineer');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(463, true, '05', '2026-07-28 15:06:16.799', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'Inside Tech', 5, '2026-07-28 15:06:16.799', 'comlbg', 60, 'training.label.insideTech');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(462, true, '04', '2026-07-28 15:06:16.579', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'Sales Engineer', 4, '2026-07-28 15:06:16.579', 'comlbg', 60, 'training.label.salesEngineer');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(461, true, '03', '2026-07-28 15:06:16.354', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'Electrical Engineer', 3, '2026-07-28 15:06:16.354', 'comlbg', 60, 'training.label.electricalEngineer');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(460, true, '02', '2026-07-28 15:06:16.111', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'Mechanic', 2, '2026-07-28 15:06:16.111', 'comlbg', 60, 'training.label.mechanic');
INSERT INTO public.code_detail
(id, is_active, code, created_at, created_by, description, extra1, extra2, extra3, extra4, extra5, "name", sort_order, updated_at, updated_by, group_id, name_msg_key)
VALUES(459, true, '01', '2026-07-28 15:06:15.867', 'comlbg', NULL, NULL, NULL, NULL, NULL, NULL, 'E & I Technician and/or Maintenance', 1, '2026-07-28 15:06:15.867', 'comlbg', 60, 'training.label.eiTechnicianMaintenance');

INSERT INTO public.message_resource
("key", ko, en, is_active, resource_type, created_by, created_at, updated_by, updated_at)
VALUES
('training.label.studentJobTitle', '교육생 직위', 'Student Job Titles', true, 'WORD', 'comgsu', '2026-06-16 17:17:33.73998', 'comgsu', '2026-06-16 17:17:57.762805'),
('training.label.areaOfInvolvement', '관련 분야', 'Area of Involvement', true, 'WORD', 'comgsu', '2026-06-16 17:20:57.493883', 'comgsu', '2026-06-16 17:20:57.493883'),
('training.label.studentProductKnowledgeLevel', '교육생의 제품 이해도', 'Student Product Knowledge Level', true, 'WORD', 'comgsu', '2026-06-16 17:25:50.625523', 'comgsu', '2026-06-16 17:25:50.625523'),
('training.label.eiTechnicianMaintenance', 'E & I Technician and/or Maintenance', 'E & I Technician and/or Maintenance', true, 'WORD', 'comlbg', '2026-07-28 15:06:08.862442', 'comlbg', '2026-07-28 15:06:08.862442'),
('training.label.mechanic', 'Mechanic', 'Mechanic', true, 'WORD', 'comlbg', '2026-07-28 15:06:09.041463', 'comlbg', '2026-07-28 15:06:09.041463'),
('training.label.electricalEngineer', 'Electrical Engineer', 'Electrical Engineer', true, 'WORD', 'comlbg', '2026-07-28 15:06:09.204215', 'comlbg', '2026-07-28 15:06:09.204215'),
('training.label.salesEngineer', 'Sales Engineer', 'Sales Engineer', true, 'WORD', 'comlbg', '2026-07-28 15:06:09.390075', 'comlbg', '2026-07-28 15:06:09.390075'),
('training.label.insideTech', 'Inside Tech', 'Inside Tech', true, 'WORD', 'comlbg', '2026-07-28 15:06:09.699517', 'comlbg', '2026-07-28 15:06:09.699517'),
('training.label.fieldServiceEngineer', 'Field Service Engineer', 'Field Service Engineer', true, 'WORD', 'comlbg', '2026-07-28 15:06:09.923354', 'comlbg', '2026-07-28 15:06:09.923354'),
('training.label.jobTitleOther', 'Other', 'Other', true, 'WORD', 'comlbg', '2026-07-28 15:06:10.129612', 'comlbg', '2026-07-28 15:06:10.129612'),
('training.label.installationStartup', 'Installation, Start-up', 'Installation, Start-up', true, 'WORD', 'comlbg', '2026-07-28 15:06:10.344196', 'comlbg', '2026-07-28 15:06:10.344196'),
('training.label.integrationParameterSetup', 'Integration and Parameter setup', 'Integration and Parameter setup', true, 'WORD', 'comlbg', '2026-07-28 15:06:10.558758', 'comlbg', '2026-07-28 15:06:10.558758'),
('training.label.fieldTroubleshooting', 'Field Troubleshooting (understanding fault codes)', 'Field Troubleshooting (understanding fault codes)', true, 'WORD', 'comlbg', '2026-07-28 15:06:10.768843', 'comlbg', '2026-07-28 15:06:10.768843'),
('training.label.salesExplanationProduct', 'Sales and Explanation of the Product', 'Sales and Explanation of the Product', true, 'WORD', 'comlbg', '2026-07-28 15:06:10.952317', 'comlbg', '2026-07-28 15:06:10.952317'),
('training.label.serialCommunications', 'Serial communications', 'Serial communications', true, 'WORD', 'comlbg', '2026-07-28 15:06:11.164137', 'comlbg', '2026-07-28 15:06:11.164137'),
('training.label.unitTroubleshooting', 'Unit troubleshooting (to determine damaged components)', 'Unit troubleshooting (to determine damaged components)', true, 'WORD', 'comlbg', '2026-07-28 15:06:11.376523', 'comlbg', '2026-07-28 15:06:11.376523'),
('training.label.involvementOther', 'Other', 'Other', true, 'WORD', 'comlbg', '2026-07-28 15:06:11.641529', 'comlbg', '2026-07-28 15:06:11.641529'),
('training.label.motorTechnology', 'Motor technology (construction, theory of operation)', 'Motor technology (construction, theory of operation)', true, 'WORD', 'comlbg', '2026-07-28 15:06:12.196593', 'comlbg', '2026-07-28 15:06:12.196593'),
('training.label.driveTechnology', 'Drive technology (construction, theory of operation)', 'Drive technology (construction, theory of operation)', true, 'WORD', 'comlbg', '2026-07-28 15:06:12.620577', 'comlbg', '2026-07-28 15:06:12.620577'),
('training.label.driveViewSoftware', 'DriveView Software', 'DriveView Software', true, 'WORD', 'comlbg', '2026-07-28 15:06:12.941121', 'comlbg', '2026-07-28 15:06:12.941121'),
('training.label.pidApplications', 'PID Applications', 'PID Applications', true, 'WORD', 'comlbg', '2026-07-28 15:06:13.23912', 'comlbg', '2026-07-28 15:06:13.23912'),
('training.label.autoTuning', 'Auto-Tuning', 'Auto-Tuning', true, 'WORD', 'comlbg', '2026-07-28 15:06:13.48612', 'comlbg', '2026-07-28 15:06:13.48612'),
('training.label.otherTopics', 'Other Topics', 'Other Topics', true, 'WORD', 'comlbg', '2026-07-28 15:06:13.992595', 'comlbg', '2026-07-28 15:06:13.992595');
