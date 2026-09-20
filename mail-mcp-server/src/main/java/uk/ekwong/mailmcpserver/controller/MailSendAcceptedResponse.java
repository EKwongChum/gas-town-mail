/*
 * Copyright 2026 ekwongchum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ekwong.mailmcpserver.controller;

/**
 * Body of a {@code 202 Accepted} response: the mail is delivered in the background.
 *
 * @param taskId id to poll with {@code GET /api/mails/tasks/{id}}
 * @param status current state of the delivery
 * @param location URL of the task resource (also sent as the {@code Location} header)
 */
public record MailSendAcceptedResponse(String taskId, String status, String location) {}
