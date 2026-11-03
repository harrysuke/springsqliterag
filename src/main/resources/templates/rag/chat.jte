@param String contextPath
@param String currentUser
@param String csrf
@param String csrfParam
@param String title
<!DOCTYPE html>
<html lang="en">
<head>
    @template.fragments.head(title = title, contextPath = contextPath)
    <style>
        .rag-chat-container {
            display: flex;
            flex-direction: column;
            height: calc(100vh - 120px);
            max-width: 900px;
            margin: 0 auto;
        }
        .rag-messages {
            flex: 1;
            overflow-y: auto;
            padding: 1rem;
            display: flex;
            flex-direction: column;
            gap: 1rem;
        }
        .rag-message {
            max-width: 85%;
            padding: 0.875rem 1.125rem;
            border-radius: var(--app-radius);
            font-size: 0.9375rem;
            line-height: 1.55;
            animation: fadeIn 0.25s ease;
        }
        .rag-message.user {
            align-self: flex-end;
            background-color: var(--app-primary);
            color: #fff;
        }
        .rag-message.assistant {
            align-self: flex-start;
            background-color: var(--app-surface);
            border: 1px solid var(--app-border);
            color: var(--app-text);
        }
        .rag-message .msg-meta {
            font-size: 0.75rem;
            opacity: 0.7;
            margin-top: 0.375rem;
        }
        .rag-message .msg-sources {
            margin-top: 0.5rem;
            padding-top: 0.5rem;
            border-top: 1px solid var(--app-border);
            font-size: 0.8125rem;
        }
        .rag-message .msg-sources a {
            color: var(--app-primary);
            text-decoration: underline;
            cursor: pointer;
        }
        .rag-input-area {
            padding: 1rem;
            border-top: 1px solid var(--app-border);
            background-color: var(--app-surface);
            border-radius: 0 0 var(--app-radius) var(--app-radius);
        }
        .rag-input-group {
            display: flex;
            gap: 0.5rem;
        }
        .rag-input-group textarea {
            flex: 1;
            resize: none;
            border: 1px solid var(--app-border);
            border-radius: var(--app-radius-sm);
            padding: 0.625rem 0.875rem;
            background-color: var(--app-bg);
            color: var(--app-text);
            font-size: 0.9375rem;
            min-height: 48px;
            max-height: 120px;
        }
        .rag-input-group textarea:focus {
            outline: none;
            border-color: var(--app-primary);
            box-shadow: 0 0 0 3px var(--app-primary-light);
        }
        .rag-input-group button {
            align-self: flex-end;
            height: 48px;
            padding: 0 1.25rem;
        }
        .rag-welcome {
            text-align: center;
            padding: 3rem 1rem;
            color: var(--app-text-muted);
        }
        .rag-welcome h3 {
            color: var(--app-text);
            font-weight: 600;
            margin-bottom: 0.5rem;
        }
        .rag-sidebar-conversations {
            max-height: 300px;
            overflow-y: auto;
        }
        .rag-conv-item {
            padding: 0.5rem 0.625rem;
            border-radius: var(--app-radius-sm);
            cursor: pointer;
            font-size: 0.8125rem;
            color: var(--app-text-muted);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            transition: background-color var(--app-transition);
        }
        .rag-conv-item:hover {
            background-color: var(--app-surface-hover);
            color: var(--app-text);
        }
        .rag-conv-item.active {
            background-color: var(--app-primary-light);
            color: var(--app-primary);
            font-weight: 600;
        }
        .rag-status {
            font-size: 0.75rem;
            padding: 0.25rem 0.625rem;
            border-radius: 999px;
            display: inline-flex;
            align-items: center;
            gap: 0.375rem;
        }
        .rag-status.online {
            background-color: rgba(25, 135, 84, 0.12);
            color: var(--app-success);
        }
        .rag-status.offline {
            background-color: rgba(220, 53, 69, 0.12);
            color: var(--app-danger);
        }
        .rag-dot {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: currentColor;
        }
        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(6px); }
            to   { opacity: 1; transform: translateY(0); }
        }
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.4; }
        }
        .rag-typing {
            display: inline-flex;
            gap: 3px;
            align-items: center;
            padding: 0.25rem 0;
        }
        .rag-typing span {
            width: 6px;
            height: 6px;
            border-radius: 50%;
            background: var(--app-text-muted);
            animation: pulse 1.2s infinite;
        }
        .rag-typing span:nth-child(2) { animation-delay: 0.2s; }
        .rag-typing span:nth-child(3) { animation-delay: 0.4s; }
    </style>
</head>
<body>
    @template.fragments.navbar(contextPath = contextPath, currentUser = currentUser, csrf = csrf, csrfParam = csrfParam)
    <div class="app-layout">
        @template.fragments.sidebar(contextPath = contextPath)
        <main class="app-content">
            <div class="d-flex align-items-center justify-content-between mb-3">
                <h1 class="app-page-title mb-0">RAG Chat</h1>
                <div id="ragHealth" class="rag-status offline">
                    <span class="rag-dot"></span>
                    <span>AI Offline</span>
                </div>
            </div>

            <div class="row g-3">
                <div class="col-lg-3 d-none d-lg-block">
                    <div class="card" style="background-color: var(--app-surface); border: 1px solid var(--app-border);">
                        <div class="card-body p-2">
                            <div class="d-flex justify-content-between align-items-center mb-2">
                                <strong style="font-size: 0.8125rem;">Conversations</strong>
                                <button class="app-btn app-btn-sm app-btn-ghost" onclick="newConversation()">New</button>
                            </div>
                            <div id="conversationList" class="rag-sidebar-conversations">
                                <!-- populated by JS -->
                            </div>
                        </div>
                    </div>
                </div>
                <div class="col-lg-9">
                    <div class="rag-chat-container card" style="border: 1px solid var(--app-border); background: var(--app-surface);">
                        <div id="messageArea" class="rag-messages">
                            <div class="rag-welcome">
                                <h3>Ask your knowledge base</h3>
                                <p class="mb-0">Upload documents via the API and chat with them using local AI.</p>
                            </div>
                        </div>
                        <div class="rag-input-area">
                            <div class="rag-input-group">
                                <textarea id="chatInput" rows="1" placeholder="Type your question..." onkeydown="handleKeyDown(event)"></textarea>
                                <button id="sendBtn" class="app-btn app-btn-primary" onclick="sendMessage()">Send</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </main>
    </div>

    @template.fragments.scripts(contextPath = contextPath, withApp = false)
    <script src="${contextPath}/rag-chat.js"></script>
</body>
</html>
