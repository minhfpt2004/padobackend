(() => {
    "use strict";

    const TOKEN_KEY = "pado.audit.token";
    const USER_KEY = "pado.audit.user";
    const state = {
        token: sessionStorage.getItem(TOKEN_KEY),
        user: readStoredUser(),
        page: 0,
        size: 20,
        totalPages: 0,
        loading: false,
        options: null,
        filters: {}
    };

    const el = Object.fromEntries([
        "loginView", "appView", "loginForm", "loginEmail", "loginPassword", "loginButton", "loginError",
        "adminEmail", "logoutButton", "filterForm", "resetFilters", "refreshButton", "pageSize",
        "actorEmailFilter", "actorIdFilter", "subjectIdFilter", "categoryFilter", "eventTypeFilter",
        "outcomeFilter", "sourceFilter", "requestIdFilter", "fromFilter", "toFilter",
        "totalEvents", "successEvents", "failureEvents", "uniqueSubjects", "resultSummary",
        "tableLoading", "emptyState", "tableWrap", "eventRows", "pagination", "previousPage",
        "nextPage", "pageIndicator", "drawerBackdrop", "detailDrawer", "closeDrawer", "detailContent",
        "toast"
    ].map(id => [id, document.getElementById(id)]));

    document.addEventListener("DOMContentLoaded", init);

    function init() {
        bindEvents();
        if (state.token && state.user?.role === "ADMIN") {
            showApplication();
            bootstrapApplication();
        } else {
            clearSession();
            showLogin();
        }
    }

    function bindEvents() {
        el.loginForm.addEventListener("submit", handleLogin);
        el.logoutButton.addEventListener("click", () => logout("Đã đăng xuất khỏi Audit Console."));
        el.filterForm.addEventListener("submit", event => {
            event.preventDefault();
            if (!captureFilters()) return;
            state.page = 0;
            loadEvents();
        });
        el.resetFilters.addEventListener("click", resetFilters);
        el.refreshButton.addEventListener("click", () => loadEvents(true));
        el.pageSize.addEventListener("change", () => {
            state.size = Number(el.pageSize.value);
            state.page = 0;
            loadEvents();
        });
        el.previousPage.addEventListener("click", () => changePage(state.page - 1));
        el.nextPage.addEventListener("click", () => changePage(state.page + 1));
        el.closeDrawer.addEventListener("click", closeDrawer);
        el.drawerBackdrop.addEventListener("click", closeDrawer);
        document.addEventListener("keydown", event => {
            if (event.key === "Escape") closeDrawer();
        });
    }

    async function bootstrapApplication() {
        try {
            await loadFilterOptions();
            captureFilters();
            await loadEvents();
        } catch (error) {
            handleRequestError(error);
        }
    }

    async function handleLogin(event) {
        event.preventDefault();
        setLoginError("");
        setButtonLoading(el.loginButton, true, "Đang xác thực…");
        try {
            const response = await fetch("/api/auth/login", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    email: el.loginEmail.value.trim(),
                    password: el.loginPassword.value
                })
            });
            const body = await readBody(response);
            if (!response.ok) throw new ApiError(response.status, body?.message || "Không thể đăng nhập");
            if (body?.user?.role !== "ADMIN") {
                throw new ApiError(403, "Tài khoản này không có quyền truy cập Audit Console.");
            }
            state.token = body.accessToken;
            state.user = body.user;
            sessionStorage.setItem(TOKEN_KEY, state.token);
            sessionStorage.setItem(USER_KEY, JSON.stringify(state.user));
            el.loginPassword.value = "";
            showApplication();
            await bootstrapApplication();
        } catch (error) {
            setLoginError(error.message || "Đăng nhập thất bại. Vui lòng thử lại.");
        } finally {
            setButtonLoading(el.loginButton, false, "Đăng nhập an toàn");
        }
    }

    async function loadFilterOptions() {
        state.options = await apiFetch("/api/admin/audit-events/filter-options");
        populateSelect(el.categoryFilter, state.options.categories, "Tất cả nhóm");
        populateSelect(el.eventTypeFilter, state.options.eventTypes, "Tất cả sự kiện");
        populateSelect(el.outcomeFilter, state.options.outcomes, "Tất cả");
        populateSelect(el.sourceFilter, state.options.sources, "Tất cả");
    }

    async function loadEvents(showRefreshToast = false) {
        if (state.loading) return;
        state.loading = true;
        setTableState("loading");
        el.refreshButton.disabled = true;
        try {
            const params = new URLSearchParams({page: String(state.page), size: String(state.size)});
            Object.entries(state.filters).forEach(([key, value]) => {
                if (value !== "" && value != null) params.set(key, value);
            });
            const result = await apiFetch(`/api/admin/audit-events?${params}`);
            state.page = result.page;
            state.totalPages = result.totalPages;
            renderEvents(result);
            if (showRefreshToast) showToast("Event stream đã được cập nhật.");
        } catch (error) {
            setTableState("empty");
            handleRequestError(error);
        } finally {
            state.loading = false;
            el.refreshButton.disabled = false;
        }
    }

    function captureFilters() {
        const from = toIso(el.fromFilter.value);
        const to = toIso(el.toFilter.value);
        if (from && to && new Date(from) > new Date(to)) {
            showToast("Thời gian bắt đầu phải trước thời gian kết thúc.", true);
            return false;
        }
        state.filters = compact({
            actorEmail: el.actorEmailFilter.value.trim(),
            actorUserId: el.actorIdFilter.value,
            subjectUserId: el.subjectIdFilter.value,
            category: el.categoryFilter.value,
            eventType: el.eventTypeFilter.value,
            outcome: el.outcomeFilter.value,
            source: el.sourceFilter.value,
            requestId: el.requestIdFilter.value.trim(),
            from,
            to
        });
        return true;
    }

    function resetFilters() {
        el.filterForm.reset();
        el.pageSize.value = String(state.size);
        state.filters = {};
        state.page = 0;
        loadEvents();
    }

    function renderEvents(result) {
        const events = Array.isArray(result.content) ? result.content : [];
        el.totalEvents.textContent = formatNumber(result.totalElements);
        el.successEvents.textContent = formatNumber(events.filter(item => item.outcome === "SUCCESS").length);
        el.failureEvents.textContent = formatNumber(events.filter(item => item.outcome === "FAILURE").length);
        el.uniqueSubjects.textContent = formatNumber(new Set(events.map(item => item.subjectUserId).filter(Boolean)).size);

        const start = result.totalElements === 0 ? 0 : result.page * result.size + 1;
        const end = Math.min((result.page + 1) * result.size, result.totalElements);
        el.resultSummary.textContent = result.totalElements
            ? `Hiển thị ${formatNumber(start)}–${formatNumber(end)} trong ${formatNumber(result.totalElements)} sự kiện`
            : "Không có sự kiện khớp bộ lọc";

        el.eventRows.replaceChildren(...events.map(eventRow));
        setTableState(events.length ? "table" : "empty");
        renderPagination(result);
    }

    function eventRow(event) {
        const row = document.createElement("tr");
        row.append(
            cell(timeContent(event.createdAt)),
            cell(eventContent(event)),
            cell(actorContent(event)),
            cell(targetContent(event)),
            cell(outcomeBadge(event.outcome)),
            cell(actionButton(event.id))
        );
        return row;
    }

    function timeContent(value) {
        const wrapper = div("time-cell");
        const date = value ? new Date(value) : null;
        wrapper.append(
            textElement("strong", date && !Number.isNaN(date.valueOf())
                ? new Intl.DateTimeFormat("vi-VN", {hour: "2-digit", minute: "2-digit", second: "2-digit"}).format(date)
                : "—"),
            textElement("span", date && !Number.isNaN(date.valueOf())
                ? new Intl.DateTimeFormat("vi-VN", {day: "2-digit", month: "2-digit", year: "numeric"}).format(date)
                : "Không rõ")
        );
        return wrapper;
    }

    function eventContent(event) {
        const wrapper = div("event-cell");
        wrapper.append(
            textElement("strong", humanize(event.eventType)),
            textElement("span", event.category || "—", "category-tag")
        );
        return wrapper;
    }

    function actorContent(event) {
        const wrapper = div("actor-cell");
        wrapper.append(
            textElement("strong", event.actorEmail || sourceLabel(event.source)),
            textElement("span", `${event.source || "—"}${event.actorUserId ? ` · Actor #${event.actorUserId}` : ""}`)
        );
        if (event.subjectUserId) {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "subject-link";
            button.textContent = `Timeline khách #${event.subjectUserId}`;
            button.addEventListener("click", () => filterBySubject(event.subjectUserId));
            wrapper.append(button);
        }
        return wrapper;
    }

    function targetContent(event) {
        const wrapper = div("target-cell");
        wrapper.append(
            textElement("strong", event.targetType || "Không có target"),
            textElement("span", event.targetId ? `#${event.targetId}` : "—")
        );
        return wrapper;
    }

    function outcomeBadge(outcome) {
        return textElement("span", outcome === "SUCCESS" ? "THÀNH CÔNG" : "THẤT BẠI",
            `badge ${outcome === "SUCCESS" ? "badge-success" : "badge-failure"}`);
    }

    function actionButton(id) {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "row-action";
        button.textContent = "→";
        button.title = "Xem chi tiết";
        button.setAttribute("aria-label", `Xem chi tiết audit event ${id}`);
        button.addEventListener("click", () => openDetail(id));
        return button;
    }

    async function openDetail(id) {
        el.drawerBackdrop.hidden = false;
        el.detailDrawer.classList.add("open");
        el.detailDrawer.setAttribute("aria-hidden", "false");
        el.detailContent.replaceChildren(detailLoading());
        try {
            const event = await apiFetch(`/api/admin/audit-events/${id}`);
            renderDetail(event);
        } catch (error) {
            closeDrawer();
            handleRequestError(error);
        }
    }

    function renderDetail(event) {
        const hero = div("detail-hero");
        hero.append(
            textElement("span", event.category || "AUDIT", "category-tag"),
            textElement("h3", humanize(event.eventType)),
            textElement("p", `${formatFullDate(event.createdAt)} · Event #${event.id}`),
            outcomeBadge(event.outcome)
        );

        const identity = detailSection("Danh tính & liên kết", [
            ["Actor", event.actorEmail || "—"],
            ["Actor ID", event.actorUserId ?? "—"],
            ["Customer ID", event.subjectUserId ?? "—"],
            ["Vai trò", event.actorRole || event.source || "—"],
            ["Target", event.targetType || "—"],
            ["Target ID", event.targetId || "—"]
        ]);
        const request = detailSection("Request context", [
            ["Request ID", event.requestId || "—"],
            ["IP address", event.ipAddress || "—"],
            ["Error code", event.errorCode || "—"],
            ["User agent", event.userAgent || "—"]
        ]);
        const metadataSection = div("detail-section");
        metadataSection.append(textElement("h4", "Metadata"));
        const pre = document.createElement("pre");
        pre.className = "metadata-view";
        pre.textContent = JSON.stringify(event.metadata || {}, null, 2);
        metadataSection.append(pre);

        el.detailContent.replaceChildren(hero, identity, request, metadataSection);
    }

    function detailSection(title, fields) {
        const section = div("detail-section");
        section.append(textElement("h4", title));
        const grid = div("detail-grid");
        fields.forEach(([label, value]) => {
            const item = div("detail-field");
            item.append(textElement("span", label), textElement("strong", String(value)));
            grid.append(item);
        });
        section.append(grid);
        return section;
    }

    function detailLoading() {
        const wrapper = div("table-state");
        wrapper.append(div("spinner"), textElement("p", "Đang tải chi tiết…"));
        return wrapper;
    }

    function closeDrawer() {
        el.detailDrawer.classList.remove("open");
        el.detailDrawer.setAttribute("aria-hidden", "true");
        window.setTimeout(() => { el.drawerBackdrop.hidden = true; }, 220);
    }

    function filterBySubject(userId) {
        el.subjectIdFilter.value = String(userId);
        el.actorIdFilter.value = "";
        el.actorEmailFilter.value = "";
        closeDrawer();
        captureFilters();
        state.page = 0;
        loadEvents();
        window.scrollTo({top: 0, behavior: "smooth"});
        showToast(`Đang hiển thị timeline khách hàng #${userId}.`);
    }

    function renderPagination(result) {
        const hasPages = result.totalPages > 0;
        el.pagination.hidden = !hasPages;
        if (!hasPages) return;
        el.pageIndicator.textContent = `Trang ${result.page + 1} / ${result.totalPages}`;
        el.previousPage.disabled = result.first;
        el.nextPage.disabled = result.last;
    }

    function changePage(page) {
        if (state.loading || page < 0 || page >= state.totalPages) return;
        state.page = page;
        loadEvents();
        document.querySelector(".ledger-card")?.scrollIntoView({behavior: "smooth", block: "start"});
    }

    async function apiFetch(path, options = {}) {
        if (!state.token) throw new ApiError(401, "Phiên đăng nhập đã hết hạn.");
        const response = await fetch(path, {
            ...options,
            headers: {
                Accept: "application/json",
                Authorization: `Bearer ${state.token}`,
                ...(options.headers || {})
            }
        });
        const body = await readBody(response);
        if (!response.ok) {
            if (response.status === 401) logout("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.", true);
            throw new ApiError(response.status, body?.message || `Yêu cầu thất bại (${response.status})`);
        }
        return body;
    }

    async function readBody(response) {
        const type = response.headers.get("content-type") || "";
        if (!type.includes("application/json")) return null;
        try { return await response.json(); } catch { return null; }
    }

    function handleRequestError(error) {
        if (error?.status === 401) return;
        showToast(error?.message || "Không thể tải dữ liệu audit.", true);
    }

    function showApplication() {
        el.loginView.hidden = true;
        el.appView.hidden = false;
        el.adminEmail.textContent = state.user?.email || "Admin";
        document.title = "PADO · Audit Console";
    }

    function showLogin() {
        el.appView.hidden = true;
        el.loginView.hidden = false;
        window.setTimeout(() => el.loginEmail.focus(), 30);
    }

    function logout(message, asError = false) {
        clearSession();
        closeDrawer();
        showLogin();
        if (message) {
            setLoginError(asError ? message : "");
            if (!asError) showToast(message);
        }
    }

    function clearSession() {
        state.token = null;
        state.user = null;
        sessionStorage.removeItem(TOKEN_KEY);
        sessionStorage.removeItem(USER_KEY);
    }

    function readStoredUser() {
        try { return JSON.parse(sessionStorage.getItem(USER_KEY) || "null"); }
        catch { return null; }
    }

    function populateSelect(select, values, placeholder) {
        const current = select.value;
        const first = document.createElement("option");
        first.value = "";
        first.textContent = placeholder;
        const options = (values || []).map(value => {
            const option = document.createElement("option");
            option.value = value;
            option.textContent = humanize(value);
            return option;
        });
        select.replaceChildren(first, ...options);
        select.value = current;
    }

    function setTableState(mode) {
        el.tableLoading.hidden = mode !== "loading";
        el.emptyState.hidden = mode !== "empty";
        el.tableWrap.hidden = mode !== "table";
        if (mode !== "table") el.pagination.hidden = true;
    }

    function setLoginError(message) {
        el.loginError.textContent = message;
        el.loginError.hidden = !message;
    }

    function setButtonLoading(button, loading, label) {
        button.disabled = loading;
        const labelNode = button.querySelector("span");
        if (labelNode) labelNode.textContent = label;
    }

    let toastTimer;
    function showToast(message, error = false) {
        window.clearTimeout(toastTimer);
        el.toast.textContent = message;
        el.toast.classList.toggle("error", error);
        el.toast.hidden = false;
        toastTimer = window.setTimeout(() => { el.toast.hidden = true; }, 3400);
    }

    function humanize(value) {
        if (!value) return "—";
        return String(value).toLowerCase().split("_").map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(" ");
    }

    function sourceLabel(source) {
        return ({SYSTEM: "Hệ thống", ANONYMOUS: "Ẩn danh", ADMIN: "Quản trị viên", USER: "Khách hàng"})[source] || "Không xác định";
    }

    function formatFullDate(value) {
        const date = value ? new Date(value) : null;
        if (!date || Number.isNaN(date.valueOf())) return "Không rõ thời gian";
        return new Intl.DateTimeFormat("vi-VN", {dateStyle: "medium", timeStyle: "medium"}).format(date);
    }

    function formatNumber(value) {
        return new Intl.NumberFormat("vi-VN").format(Number(value) || 0);
    }

    function toIso(value) {
        if (!value) return "";
        const date = new Date(value);
        return Number.isNaN(date.valueOf()) ? "" : date.toISOString();
    }

    function compact(object) {
        return Object.fromEntries(Object.entries(object).filter(([, value]) => value !== "" && value != null));
    }

    function cell(content) {
        const td = document.createElement("td");
        td.append(content);
        return td;
    }

    function div(className) {
        const element = document.createElement("div");
        if (className) element.className = className;
        return element;
    }

    function textElement(tag, text, className) {
        const element = document.createElement(tag);
        if (className) element.className = className;
        element.textContent = text;
        return element;
    }

    class ApiError extends Error {
        constructor(status, message) {
            super(message);
            this.status = status;
        }
    }
})();
