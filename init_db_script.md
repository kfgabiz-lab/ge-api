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