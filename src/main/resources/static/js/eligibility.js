(function () {
    'use strict';
    var page = window.ChinaVisaPage || {}, zh = page.lang === 'zh';
    var byId = function (id) { return document.getElementById(id); };
    var mode = byId('entryMode'), revision = 0, started = false, busy = false, needsItinerary = false;
    var previousHours = false, durations = {days: '7', hours: '12'};
    if (!mode) return;
    function tr(en, cn) { return zh ? cn : en; }
    function track(name, extra) {
        if (typeof window.visaTrack === 'function') window.visaTrack(name, Object.assign({ country_code: page.code, route: mode.value, language: page.lang }, extra || {}));
    }
    function showFields() {
        var transit = mode.value === 'transit240' || mode.value === 'transit24' || (mode.value === 'auto' && needsItinerary);
        byId('itineraryFields').style.display = transit ? 'block' : 'none';
        byId('transit240Fields').style.display = transit ? 'block' : 'none';
        byId('transit24Fields').style.display = mode.value === 'transit24' ? 'block' : 'none';
        byId('hainanFields').style.display = mode.value === 'hainan' ? 'block' : 'none';
        byId('departureDateField').style.display = mode.value === 'transit24' ? 'none' : 'block';
        var hours = mode.value === 'transit24', input = byId('days');
        var passportConfirmation=byId('passportValid').closest('.field');
        if(passportConfirmation)passportConfirmation.style.display=hours?'none':'block';
        if(hours !== previousHours){ durations[previousHours?'hours':'days']=input.value; input.value=durations[hours?'hours':'days']; previousHours=hours; }
        byId('duration-label').textContent = hours ? tr('Planned transit (hours)', '计划过境时间（小时）') : tr('Planned stay (whole days)', '计划停留（整数天）');
        input.min = hours ? '0.1' : '1'; input.max = hours ? '24' : '3650'; input.step = hours ? '0.1' : '1';
        input.disabled = !hours && Boolean(byId('departureDate').value);
        var calc = byId('calculate-btn'); if (calc) calc.disabled = busy || !['direct','auto'].includes(mode.value);
    }
    function inputData() {
        return { passport: byId('passport').value, passportValid: byId('passportValid').checked,
            purpose: byId('purpose').value, days: byId('days').value, entryDate: byId('entryDate').value,
            departureDate: mode.value === 'transit24' ? '' : byId('departureDate').value,
            entryMode: mode.value, origin: byId('origin').value, destination: byId('destination').value,
            onward: byId('onward').checked };
    }
    function addText(parent, tag, text) { var el = document.createElement(tag); el.textContent = text; parent.appendChild(el); return el; }
    function addLink(parent, url, label, action) {
        if (!url || (!url.startsWith('/') && !url.startsWith('#') && !url.startsWith('https://'))) return;
        var a = document.createElement('a'); a.href = url; a.textContent = label; a.dataset.action = action; parent.appendChild(a);
    }
    function render(reply, target) {
        target.replaceChildren(); target.className = 'result ' + (reply.status === 'eligible' ? 'ok' : reply.status === 'ineligible' || reply.status === 'invalid' ? 'bad' : 'warn');
        target.style.display = 'block';
        target.tabIndex = -1;
        target.focus({preventScroll:true});
        target.scrollIntoView({block:'nearest',behavior:'smooth'});
        addText(target, 'h3', reply.status === 'eligible' ? tr('Your trip meets the basic visa-free requirements', '你的行程符合免签基础条件') : reply.status === 'ineligible' ? tr('This trip does not meet this route’s requirements', '本行程不符合这条路径的条件') : reply.status === 'invalid' ? tr('Check your trip details', '请检查行程信息') : tr('More checks are needed before you book', '预订前还需核对以下条件'));
        addText(target, 'p', zh ? reply.messageZh : reply.messageEn);
        if (reply.recommendedRoute) addText(target, 'p', tr('Route checked: ', '本次核对路径：') + (reply.recommendedRoute === 'direct' ? tr('General visa-free entry', '一般直接免签') : tr('240-hour transit', '240小时过境')));
        var checks = {entry_exit_ports:tr('Confirm both ports against the official list.', '按官方名单核对入境及出境口岸。'), permitted_areas:tr('Check every city you plan to visit against the permitted areas.', '逐一核对计划到访城市是否位于允许区域。'), travel_documents:tr('Verify your travel document and its required validity.', '核对旅行证件类型及所需有效期。'), admitted_timing:tr('Confirm the departure deadline recorded by immigration.', '核对边检实际注明的停留截止时间。'), temporary_entry_permission:tr('If leaving the restricted area, apply for a temporary entry permit.', '如需离开口岸限定区域，申请临时入境许可。'), hainan_ports:tr('Confirm arrival and departure through open Hainan ports.', '确认通过海南对外开放口岸入出境。'), hainan_only:tr('Keep all activities within Hainan Province.', '所有活动须限于海南省内。'), agreement_conditions:tr('Check your agreement’s passport, purpose and counting rules.', '核对具体协定的护照、事由及计时规则。'), previous_stays:tr('Check previous stays and any cumulative limit under your agreement.', '按具体协定核对既往停留及累计上限。')};
        if (Array.isArray(reply.missingChecks)) { var list = document.createElement('ul'); target.appendChild(list); reply.missingChecks.forEach(function(key){ if(checks[key]) addText(list,'li',checks[key]); }); }
        if (reply.verifiedAsOf) addText(target,'p',tr('Rules last checked: ', '规则最近核对：') + reply.verifiedAsOf);
        if (reply.indicativeLatestDeparture) addText(target,'p',tr('Indicative final day, subject to admission: ', '基础条件满足并获准入境时的参考最后停留日：') + reply.indicativeLatestDeparture + tr(' (China time)', '（中国时间）'));
        if (reply.countingStart) addText(target, 'p', tr('Day 1: ', '第1天：') + reply.countingStart);
        if (reply.plannedDeparture) addText(target, 'p', tr('Departure date for the duration entered: ', '按所填天数计算的离境日期：') + reply.plannedDeparture);
        if (reply.latestDeparture) { addText(target, 'p', tr('Latest departure under the recorded 30-day rule: ', '记录的30天规则最晚离境日期：') + reply.latestDeparture + tr(' (China time)', '（中国时间）')); addText(target, 'p', zh ? reply.departureNoteZh : reply.departureNoteEn); }
        if (reply.perEntryDays) addText(target, 'p', tr('Listed per-entry days: ', '记录的单次天数：') + reply.perEntryDays);
        if (reply.cumulativeDays && reply.cumulativeWindowDays) addText(target, 'p', zh ? ('任意' + reply.cumulativeWindowDays + '天内累计不超过' + reply.cumulativeDays + '天；历史停留未核验。') : (reply.cumulativeDays + ' days within any ' + reply.cumulativeWindowDays + '-day period; previous stays not checked.'));
        var links = document.createElement('div'); links.className = 'links'; target.appendChild(links);
        if (reply.officialSource) addLink(links, reply.officialSource, tr('Verify the official source', '核对官方来源'), 'official_source_click');
        var policy = (reply.recommendedRoute || mode.value) === 'transit240' ? 'transit' : mode.value === 'hainan' ? 'hainan' : null;
        addLink(links, page.generalTransit ? '/visa-guide?lang=' + page.lang : '/country/' + encodeURIComponent(page.code) + (policy ? '/' + policy : '') + '?lang=' + page.lang, page.generalTransit ? tr('Visa options if this connection does not fit', '不符合时查看签证路径') : tr('Review country rules', '查看国家规则'), 'next_step_click');
        if(reply.status !== 'eligible' && !page.generalTransit)addLink(links,'/visa-guide?lang='+page.lang,tr('Check visa options','查看签证路径'),'next_step_click');
        if(mode.value === 'transit24')addLink(links,'/articles/leave-airport-china-24-hour-transit?lang='+page.lang,tr('Can I leave the airport?','可以离开机场吗？'),'next_step_click');
        if(policy === 'transit')addLink(links,'/articles/multi-city-travel-240-hour-china-transit?lang='+page.lang,tr('Check travel between cities','核对多城市活动范围'),'next_step_click');
        if (reply.status === 'eligible') {
            addLink(links, '/articles/return-onward-ticket-china-visafree?lang=' + page.lang, tr('Ticket preparation', '客票准备'), 'next_step_click');
            addLink(links, '/articles/24h-accommodation-registration-china?lang=' + page.lang, tr('Accommodation registration', '住宿登记'), 'next_step_click');
        }
    }
    async function submit(calculate) {
        if (busy) return;
        if (byId('nationality')) { if(!byId('nationality').reportValidity())return; page.code=byId('nationality').value; }
        if (!byId('entryDate').reportValidity() || !byId('days').reportValidity()) return;
        var target = byId(calculate ? 'calculator-result' : 'check-result'), current = revision, data = inputData();
        var controller = new AbortController(), timeout = window.setTimeout(function(){controller.abort();},20000);
        busy = true; byId('check-btn').disabled = true; if(byId('calculate-btn'))byId('calculate-btn').disabled = true;
        target.style.display = 'block'; target.className = 'result'; target.textContent = tr('Checking…', '正在核验…');
        try {
            var response = await fetch('/api/eligibility/' + encodeURIComponent(page.code) + (calculate ? '/calculate' : ''), { method: 'POST', headers: { 'Content-Type': 'application/json' }, cache: 'no-store', signal:controller.signal, body: JSON.stringify(data) });
            if (!response.ok) throw new Error('request_failed');
            var reply = await response.json();
            if (!reply || !['eligible', 'pending', 'ineligible', 'invalid'].includes(reply.status)) throw new Error('invalid_response');
            if (current !== revision) return;
            render(reply, target);
            if(reply.needsItinerary){needsItinerary=true;showFields();addText(target,'p',tr('Add the countries or regions immediately before and after China, confirm your onward ticket, then check again. For a short airside connection, select the 24-hour option.', '请补充紧邻中国前后的国家或地区并确认联程客票，然后再次检查。如只是短时口岸内中转，请选择24小时选项。'));byId('origin').focus();}
            track(calculate ? 'date_calculation_result' : 'checker_result', { result_status: reply.status, result_reason: reply.reason });
            if (calculate && reply.latestDeparture) track('date_calculation_complete', { result_status: reply.status });
            // A useful screening can also find an ineligible trip or specific checks
            // still required. Keep success separate from the historical eligible event.
            if (!calculate && reply.status !== 'invalid' && !reply.needsItinerary) track('checker_screening_complete', { result_status: reply.status, result_reason: reply.reason });
            if (!calculate && reply.status === 'eligible') track('checker_complete', { result_status: reply.status });
            if (!calculate && reply.status === 'pending') track('checker_pending', { result_status: reply.status, result_reason: reply.reason });
        } catch (error) {
            if (current !== revision) return;
            render({ status: 'pending', messageEn: 'The check could not be completed. No eligibility decision has been made. Retry or verify the official source.', messageZh: '本次核验未完成，未作出资格判断。请重试或查阅官方来源。' }, target);
            track('checker_error', { result_reason:'request_failed' });
        } finally { window.clearTimeout(timeout); busy = false; byId('check-btn').disabled = false; showFields(); }
    }
    var params = new URLSearchParams(window.location.search), requested = params.get('entryMode') || page.selectedEntryMode;
    if (['auto', 'direct', 'transit240', 'transit24', 'hainan'].includes(requested)) mode.value = requested;
    if (params.get('purpose') === 'transit') byId('purpose').value = 'transit';
    showFields();
    document.querySelectorAll('#eligibility-checker input, #eligibility-checker select').forEach(function (input) {
        function invalidate() {
            if(input.id === 'nationality')page.code=input.value;
            revision++; byId('check-result').style.display = 'none';
            if (byId('calculator-result')) byId('calculator-result').style.display = 'none';
            if (!started) { started = true; track('checker_start'); }
            if (input === mode) { needsItinerary=false; showFields(); track('route_selected'); }
            if (input.id === 'departureDate') showFields();
        }
        input.addEventListener(input.tagName === 'SELECT' || input.type === 'checkbox' ? 'change' : 'input', invalidate);
    });
    byId('check-btn').addEventListener('click', function () { if (!started) { started = true; track('checker_start'); } submit(false); });
    if (byId('calculate-btn')) byId('calculate-btn').addEventListener('click', function () { submit(true); });
    var hashTarget = window.location.hash === '#stay-calculator' ? byId('stay-calculator') : window.location.hash === '#eligibility-checker' ? byId('eligibility-checker') : null;
    if (hashTarget) hashTarget.scrollIntoView({ block: 'start' });
}());
