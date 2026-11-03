(function() {
    'use strict';

    const ctx = window.CONTEXT_PATH || '';
    const messageArea = document.getElementById('messageArea');
    const chatInput = document.getElementById('chatInput');
    const sendBtn = document.getElementById('sendBtn');
    const healthEl = document.getElementById('ragHealth');
    const convList = document.getElementById('conversationList');

    let sessionId = localStorage.getItem('ragSessionId') || null;
    let isStreaming = false;

    chatInput.addEventListener('input', function() {
        this.style.height = 'auto';
        this.style.height = Math.min(this.scrollHeight, 120) + 'px';
    });

    window.handleKeyDown = function(e) {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    };

    function scrollToBottom() {
        messageArea.scrollTop = messageArea.scrollHeight;
    }

    function appendMessage(role, html, meta) {
        const div = document.createElement('div');
        div.className = 'rag-message ' + role;
        div.innerHTML = html + (meta ? '<div class="msg-meta">' + meta + '</div>' : '');
        messageArea.appendChild(div);
        scrollToBottom();
        return div;
    }

    function setTyping(visible) {
        let el = document.getElementById('typingIndicator');
        if (!visible) {
            if (el) el.remove();
            return;
        }
        if (el) return;
        el = document.createElement('div');
        el.id = 'typingIndicator';
        el.className = 'rag-message assistant';
        el.innerHTML = '<div class="rag-typing"><span></span><span></span><span></span></div>';
        messageArea.appendChild(el);
        scrollToBottom();
    }

    window.sendMessage = function() {
        const text = chatInput.value.trim();
        if (!text || isStreaming) return;

        // Clear welcome on first message
        const welcome = messageArea.querySelector('.rag-welcome');
        if (welcome) welcome.remove();

        appendMessage('user', escapeHtml(text));
        chatInput.value = '';
        chatInput.style.height = 'auto';
        isStreaming = true;
        sendBtn.disabled = true;
        sendBtn.textContent = '...';

        const assistantEl = appendMessage('assistant', '');
        setTyping(true);

        const body = JSON.stringify({ message: text, sessionId: sessionId });
        const eventSource = new EventSource(ctx + '/api/rag/chat/stream');

        // EventSource doesn't support POST, so we use fetch with ReadableStream
        fetch(ctx + '/api/rag/chat/stream', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'text/event-stream'
            },
            body: body
        }).then(response => {
            if (!response.ok) throw new Error('HTTP ' + response.status);
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';

            function read() {
                reader.read().then(({ done, value }) => {
                    if (done) {
                        finishStream();
                        return;
                    }
                    buffer += decoder.decode(value, { stream: true });
                    const lines = buffer.split('\n');
                    buffer = lines.pop();
                    for (const line of lines) {
                        processLine(line);
                    }
                    read();
                }).catch(err => {
                    console.error('Stream read error', err);
                    finishStream();
                });
            }
            read();
        }).catch(err => {
            console.error('Fetch error', err);
            setTyping(false);
            assistantEl.innerHTML = '<span style="color:var(--app-danger)">Error: ' + escapeHtml(err.message) + '</span>';
            finishStream();
        });

        function processLine(line) {
            if (!line.startsWith('data:')) return;
            const json = line.substring(5).trim();
            if (!json) return;
            try {
                const data = JSON.parse(json);
                if (data.sessionId) sessionId = data.sessionId;
                if (data.done) {
                    setTyping(false);
                    if (data.sources && data.sources.length > 0) {
                        const srcHtml = '<div class="msg-sources">Sources: ' +
                            data.sources.map(s => '<span title="' + escapeHtml(s.snippet) + '">[' + (s.chunkIndex + 1) + ']</span>').join(' ') +
                            '</div>';
                        assistantEl.insertAdjacentHTML('beforeend', srcHtml);
                    }
                } else {
                    setTyping(false);
                    assistantEl.insertAdjacentText('beforeend', data.content);
                    scrollToBottom();
                }
            } catch (e) {
                // ignore parse errors for keep-alive lines
            }
        }

        function finishStream() {
            isStreaming = false;
            sendBtn.disabled = false;
            sendBtn.textContent = 'Send';
            if (sessionId) localStorage.setItem('ragSessionId', sessionId);
            loadConversations();
        }
    };

    window.newConversation = function() {
        sessionId = null;
        localStorage.removeItem('ragSessionId');
        messageArea.innerHTML = '<div class="rag-welcome"><h3>Ask your knowledge base</h3><p class="mb-0">Upload documents via the API and chat with them using local AI.</p></div>';
        loadConversations();
    };

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function checkHealth() {
        axios.get(ctx + '/api/rag/health')
            .then(({ data }) => {
                if (data.ollama) {
                    healthEl.className = 'rag-status online';
                    healthEl.innerHTML = '<span class="rag-dot"></span><span>AI Online</span>';
                } else {
                    healthEl.className = 'rag-status offline';
                    healthEl.innerHTML = '<span class="rag-dot"></span><span>AI Offline</span>';
                }
            })
            .catch(() => {
                healthEl.className = 'rag-status offline';
                healthEl.innerHTML = '<span class="rag-dot"></span><span>AI Offline</span>';
            });
    }

    function loadConversations() {
        axios.get(ctx + '/api/rag/conversations')
            .then(({ data }) => {
                convList.innerHTML = '';
                if (!data || data.length === 0) {
                    convList.innerHTML = '<div class="text-muted" style="font-size:0.75rem;padding:0.5rem;">No conversations yet</div>';
                    return;
                }
                data.forEach(c => {
                    const div = document.createElement('div');
                    div.className = 'rag-conv-item' + (c.sessionId === sessionId ? ' active' : '');
                    div.textContent = c.title || 'Untitled';
                    div.title = c.title || 'Untitled';
                    div.onclick = () => loadConversation(c.sessionId);
                    convList.appendChild(div);
                });
            })
            .catch(err => {
                console.error('Failed to load conversations', err);
            });
    }

    function loadConversation(sid) {
        sessionId = sid;
        localStorage.setItem('ragSessionId', sid);
        axios.get(ctx + '/api/rag/conversations/' + encodeURIComponent(sid))
            .then(({ data }) => {
                messageArea.innerHTML = '';
                data.messages.forEach(m => {
                    const meta = m.sources && m.sources.length ? 'Sources: ' + m.sources.map(s => '[' + (s.chunkIndex + 1) + ']').join(' ') : '';
                    appendMessage(m.role, escapeHtml(m.content), meta);
                });
                loadConversations();
            })
            .catch(err => {
                console.error('Failed to load conversation', err);
            });
    }

    checkHealth();
    setInterval(checkHealth, 15000);
    loadConversations();
})();
