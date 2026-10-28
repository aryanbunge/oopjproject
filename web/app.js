/**
 * Weather Anomaly Detector — Frontend Application
 * Features:
 * - Hardware-accelerated calm ocean wave WebGL background
 * - Real-time threshold re-evaluation engine
 * - SVG temperature trend curve & precipitation bars
 */

(function () {
    'use strict';

    // Global Application State
    const state = {
        mode: 'live', // 'live' | 'csv'
        city: 'London,GB',
        apiKey: localStorage.getItem('openweather_api_key') || '',
        activeTitle: 'Loading weather data...',
        records: [], // Array of { date, temperatureCelsius, rainfallMm, windSpeedKmh }
        results: [], // Array of DetectionResult objects
        thresholds: {
            maxTemp: 40.0,
            minTemp: 0.0,
            maxRain: 100.0,
            maxWind: 80.0,
            maxDelta: 10.0
        },
        dominantCondition: 'nominal' // 'nominal' | 'heat' | 'cold' | 'rain' | 'storm'
    };

    // DOM Elements Cache
    const elements = {
        canvas: document.getElementById('atmosphere-canvas'),
        beacon: document.getElementById('system-beacon'),
        geoTelemetry: document.getElementById('geo-telemetry'),
        utcClock: document.getElementById('utc-clock'),
        modeLive: document.getElementById('mode-live'),
        modeCsv: document.getElementById('mode-csv'),
        liveControls: document.getElementById('live-controls'),
        csvControls: document.getElementById('csv-controls'),
        cityInput: document.getElementById('city-input'),
        btnFetchForecast: document.getElementById('btn-fetch-forecast'),
        btnToggleKey: document.getElementById('btn-toggle-key'),
        keyPopover: document.getElementById('key-popover'),
        apiKeyInput: document.getElementById('api-key-input'),
        btnSaveKey: document.getElementById('btn-save-key'),
        keyStatusText: document.getElementById('key-status-text'),
        btnLoadSample: document.getElementById('btn-load-sample'),
        csvFileInput: document.getElementById('csv-file-input'),
        btnOpenPasteModal: document.getElementById('btn-open-paste-modal'),
        csvModal: document.getElementById('csv-modal'),
        btnCloseModal: document.getElementById('btn-close-modal'),
        btnModalCancel: document.getElementById('btn-modal-cancel'),
        btnModalSubmit: document.getElementById('btn-modal-submit'),
        csvPasteTextarea: document.getElementById('csv-paste-textarea'),
        chartSourceTitle: document.getElementById('chart-source-title'),
        horizonSvg: document.getElementById('horizon-svg'),
        chartTooltip: document.getElementById('chart-tooltip'),
        ttDate: document.getElementById('tt-date'),
        ttTemp: document.getElementById('tt-temp'),
        ttRain: document.getElementById('tt-rain'),
        ttWind: document.getElementById('tt-wind'),
        ttStatus: document.getElementById('tt-status'),
        observationMatrix: document.getElementById('observation-matrix'),
        matrixCountBadge: document.getElementById('matrix-count-badge'),
        statTotalDays: document.getElementById('stat-total-days'),
        statAnomalyDays: document.getElementById('stat-anomaly-days'),
        statAnomalyPercent: document.getElementById('stat-anomaly-percent'),
        statAnomalyCell: document.getElementById('stat-anomaly-cell'),
        statPeakTemp: document.getElementById('stat-peak-temp'),
        statLowTemp: document.getElementById('stat-low-temp'),
        statMaxWind: document.getElementById('stat-max-wind'),
        statTotalRain: document.getElementById('stat-total-rain'),
        btnResetRules: document.getElementById('btn-reset-rules'),
        toastBanner: document.getElementById('toast-banner'),
        toastMessage: document.getElementById('toast-message'),
        sliders: {
            maxTemp: document.getElementById('slider-max-temp'),
            minTemp: document.getElementById('slider-min-temp'),
            maxRain: document.getElementById('slider-max-rain'),
            maxWind: document.getElementById('slider-max-wind'),
            maxDelta: document.getElementById('slider-max-delta')
        },
        sliderValues: {
            maxTemp: document.getElementById('val-max-temp'),
            minTemp: document.getElementById('val-min-temp'),
            maxRain: document.getElementById('val-max-rain'),
            maxWind: document.getElementById('val-max-wind'),
            maxDelta: document.getElementById('val-max-delta')
        }
    };

    // -------------------------------------------------------------------------
    // Calm Ocean Wave WebGL Canvas with Mouse Wake Physics
    // -------------------------------------------------------------------------
    let gl, shaderProgram, positionBuffer;
    let uTimeLoc, uResLoc, uMouseLoc, uVelLoc, uBaseColLoc, uAccentColLoc, uTurbLoc;

    const mouse = {
        x: window.innerWidth * 0.5,
        y: window.innerHeight * 0.5,
        targetX: window.innerWidth * 0.5,
        targetY: window.innerHeight * 0.5,
        speed: 0,
        smoothSpeed: 0
    };

    // Ocean Color States
    const PALETTES = {
        base: [0.93, 0.96, 0.98],   // Pale coastal alabaster mist
        nominal: [0.08, 0.52, 0.72], // Ocean Cerulean
        heat: [0.88, 0.55, 0.35],    // Sunset Amber
        rain: [0.12, 0.65, 0.60],    // Deep Sea Teal
        storm: [0.82, 0.38, 0.32],   // Storm Coral
        cold: [0.22, 0.48, 0.75]     // Glacial Blue
    };

    const shaderState = {
        currentAccent: [...PALETTES.nominal],
        targetAccent: [...PALETTES.nominal],
        turbulence: 0.8,
        targetTurbulence: 0.8
    };

    const vertexShaderSource = `
        attribute vec2 a_position;
        void main() {
            gl_Position = vec4(a_position, 0.0, 1.0);
        }
    `;

    // Realistic, serene ocean waves fragment shader
    const fragmentShaderSource = `
        precision highp float;
        uniform vec2 u_resolution;
        uniform float u_time;
        uniform vec2 u_mouse;
        uniform float u_velocity;
        uniform vec3 u_base_color;
        uniform vec3 u_accent_color;
        uniform float u_turbulence;

        void main() {
            vec2 st = gl_FragCoord.xy / u_resolution.xy;
            st.y = 1.0 - st.y;
            vec2 aspect = vec2(u_resolution.x / u_resolution.y, 1.0);
            vec2 uv = st * aspect;

            // Slow, serene ocean wave time
            float t = u_time * 0.22 * u_turbulence;

            // Gentle mouse wake ripples
            vec2 mouseUv = (u_mouse / u_resolution.xy) * aspect;
            vec2 toMouse = uv - mouseUv;
            float mouseDist = length(toMouse);
            float mouseWave = sin(mouseDist * 16.0 - u_time * 1.8) * exp(-mouseDist * 3.2) * clamp(u_velocity * 0.025, 0.0, 0.45);
            vec2 waveUv = uv + normalize(toMouse + 0.001) * mouseWave * 0.06;

            // Layered oceanic swells (directional waves)
            float h = 0.0;
            // Rolling groundswell
            h += sin(dot(waveUv, normalize(vec2(0.85, 0.45))) * 3.4 + t * 0.75) * 0.38;
            // Secondary cross-swell
            h += sin(dot(waveUv, normalize(vec2(-0.55, 0.8))) * 5.2 + t * 0.55) * 0.24;
            // Gentle surface ripples
            h += sin(dot(waveUv, normalize(vec2(0.3, 0.95))) * 10.0 + t * 1.1) * 0.12;
            // Fine capillary texture
            h += cos(dot(waveUv, normalize(vec2(-0.75, -0.4))) * 18.0 + t * 1.3) * 0.05;

            // Sun caustics shimmering beneath the surface
            float c1 = sin(waveUv.x * 14.0 + h * 3.5 + t * 0.9) * cos(waveUv.y * 14.0 + h * 3.5 + t * 0.8);
            float c2 = cos(waveUv.x * 20.0 - t * 0.6) * sin(waveUv.y * 20.0 + t * 0.7);
            float caustic = pow(clamp((c1 + c2) * 0.5 + 0.5, 0.0, 1.0), 4.5) * 0.22;

            // Natural ocean depth gradient
            vec3 deepOcean = vec3(0.06, 0.20, 0.32);     // Deep maritime navy
            vec3 midWater  = vec3(0.18, 0.48, 0.62);     // Coastal aquamarine
            vec3 shallowMist = vec3(0.92, 0.95, 0.98);   // Pale seafoam mist

            float waveFactor = clamp(h * 0.5 + 0.5, 0.0, 1.0);
            vec3 waterColor = mix(deepOcean, midWater, waveFactor);

            // Blend gently with top mist so text remains effortlessly legible
            vec3 ocean = mix(shallowMist, waterColor, 0.26 + 0.14 * st.y);

            // Reactive tint based on current weather condition
            ocean = mix(ocean, u_accent_color, clamp(waveFactor * 0.32, 0.0, 0.4));

            // Sunlight caustics on wave peaks
            ocean += vec3(0.92, 0.98, 1.0) * caustic * 0.32;

            // Soft wave crest highlight
            float crest = smoothstep(0.42, 0.56, h);
            ocean += vec3(0.12, 0.18, 0.22) * crest * 0.12;

            gl_FragColor = vec4(ocean, 1.0);
        }
    `;

    function initWebGL() {
        if (!elements.canvas) return;
        gl = elements.canvas.getContext('webgl', { antialias: true, alpha: false, depth: false });
        if (!gl) {
            console.warn('WebGL unavailable, using static backdrop.');
            return;
        }

        function createShader(gl, type, source) {
            const shader = gl.createShader(type);
            gl.shaderSource(shader, source);
            gl.compileShader(shader);
            if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
                console.error(gl.getShaderInfoLog(shader));
                gl.deleteShader(shader);
                return null;
            }
            return shader;
        }

        const vs = createShader(gl, gl.VERTEX_SHADER, vertexShaderSource);
        const fs = createShader(gl, gl.FRAGMENT_SHADER, fragmentShaderSource);
        shaderProgram = gl.createProgram();
        gl.attachShader(shaderProgram, vs);
        gl.attachShader(shaderProgram, fs);
        gl.linkProgram(shaderProgram);

        if (!gl.getProgramParameter(shaderProgram, gl.LINK_STATUS)) {
            console.error('Shader Program Link Error:', gl.getProgramInfoLog(shaderProgram));
            return;
        }

        gl.useProgram(shaderProgram);

        const positionAttr = gl.getAttribLocation(shaderProgram, 'a_position');
        positionBuffer = gl.createBuffer();
        gl.bindBuffer(gl.ARRAY_BUFFER, positionBuffer);
        gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([
            -1, -1,
             1, -1,
            -1,  1,
            -1,  1,
             1, -1,
             1,  1,
        ]), gl.STATIC_DRAW);

        gl.enableVertexAttribArray(positionAttr);
        gl.vertexAttribPointer(positionAttr, 2, gl.FLOAT, false, 0, 0);

        uTimeLoc = gl.getUniformLocation(shaderProgram, 'u_time');
        uResLoc = gl.getUniformLocation(shaderProgram, 'u_resolution');
        uMouseLoc = gl.getUniformLocation(shaderProgram, 'u_mouse');
        uVelLoc = gl.getUniformLocation(shaderProgram, 'u_velocity');
        uBaseColLoc = gl.getUniformLocation(shaderProgram, 'u_base_color');
        uAccentColLoc = gl.getUniformLocation(shaderProgram, 'u_accent_color');
        uTurbLoc = gl.getUniformLocation(shaderProgram, 'u_turbulence');

        resizeCanvas();
        window.addEventListener('resize', resizeCanvas, { passive: true });
        window.addEventListener('mousemove', onMouseMove, { passive: true });

        requestAnimationFrame(renderLoop);
    }

    function resizeCanvas() {
        if (!elements.canvas || !gl) return;
        const width = window.innerWidth;
        const height = window.innerHeight;
        if (elements.canvas.width !== width || elements.canvas.height !== height) {
            elements.canvas.width = width;
            elements.canvas.height = height;
            gl.viewport(0, 0, width, height);
        }
    }

    let lastMouseTime = performance.now();
    let lastX = window.innerWidth * 0.5;
    let lastY = window.innerHeight * 0.5;

    function onMouseMove(e) {
        mouse.targetX = e.clientX;
        mouse.targetY = e.clientY;

        const now = performance.now();
        const dt = Math.max(1, now - lastMouseTime);
        const dx = e.clientX - lastX;
        const dy = e.clientY - lastY;
        const dist = Math.sqrt(dx * dx + dy * dy);

        mouse.speed = dist / dt * 80.0;
        lastX = e.clientX;
        lastY = e.clientY;
        lastMouseTime = now;
    }

    const startTime = performance.now();

    function renderLoop(now) {
        if (!gl || !shaderProgram) return;

        // Smooth mouse spring physics
        mouse.x += (mouse.targetX - mouse.x) * 0.06;
        mouse.y += (mouse.targetY - mouse.y) * 0.06;
        mouse.smoothSpeed += (mouse.speed - mouse.smoothSpeed) * 0.05;
        mouse.speed *= 0.94; // Gentle decay

        // Smoothly interpolate palette colors
        for (let i = 0; i < 3; i++) {
            shaderState.currentAccent[i] += (shaderState.targetAccent[i] - shaderState.currentAccent[i]) * 0.03;
        }
        shaderState.turbulence += (shaderState.targetTurbulence - shaderState.turbulence) * 0.03;

        gl.useProgram(shaderProgram);
        // Slower time progression for a peaceful ocean feel
        gl.uniform1f(uTimeLoc, (now - startTime) * 0.0006);
        gl.uniform2f(uResLoc, elements.canvas.width, elements.canvas.height);
        gl.uniform2f(uMouseLoc, mouse.x, mouse.y);
        gl.uniform1f(uVelLoc, mouse.smoothSpeed);
        gl.uniform3fv(uBaseColLoc, PALETTES.base);
        gl.uniform3fv(uAccentColLoc, shaderState.currentAccent);
        gl.uniform1f(uTurbLoc, shaderState.turbulence);

        gl.drawArrays(gl.TRIANGLES, 0, 6);

        requestAnimationFrame(renderLoop);
    }

    function updateAtmosphericState(condition, hasSevereAnomalies) {
        state.dominantCondition = condition;
        let targetPalette = PALETTES.nominal;
        let turb = 0.8;

        if (condition === 'heat') {
            targetPalette = PALETTES.heat;
            turb = 1.0;
        } else if (condition === 'rain') {
            targetPalette = PALETTES.rain;
            turb = 1.1;
        } else if (condition === 'storm' || hasSevereAnomalies) {
            targetPalette = PALETTES.storm;
            turb = 1.3;
        } else if (condition === 'cold') {
            targetPalette = PALETTES.cold;
            turb = 0.7;
        }

        shaderState.targetAccent = [...targetPalette];
        shaderState.targetTurbulence = turb;

        if (elements.beacon) {
            if (hasSevereAnomalies) {
                elements.beacon.classList.add('alert');
            } else {
                elements.beacon.classList.remove('alert');
            }
        }
    }

    // -------------------------------------------------------------------------
    // Anomaly Detection Engine (Mirrors Java AnomalyDetector)
    // -------------------------------------------------------------------------
    function evaluateAnomalies(records, thresholds) {
        const results = [];
        let previous = null;

        for (const record of records) {
            const anomalies = [];
            const temp = record.temperatureCelsius;
            const rain = record.rainfallMm;
            const wind = record.windSpeedKmh;

            if (temp > thresholds.maxTemp) {
                anomalies.push({
                    type: "High temperature",
                    explanation: `${temp.toFixed(1)}°C is above ${thresholds.maxTemp.toFixed(1)}°C`
                });
            }
            if (temp < thresholds.minTemp) {
                anomalies.push({
                    type: "Low temperature",
                    explanation: `${temp.toFixed(1)}°C is below ${thresholds.minTemp.toFixed(1)}°C`
                });
            }
            if (rain > thresholds.maxRain) {
                anomalies.push({
                    type: "Heavy rainfall",
                    explanation: `${rain.toFixed(1)} mm is above ${thresholds.maxRain.toFixed(1)} mm`
                });
            }
            if (wind > thresholds.maxWind) {
                anomalies.push({
                    type: "Strong wind",
                    explanation: `${wind.toFixed(1)} km/h is above ${thresholds.maxWind.toFixed(1)} km/h`
                });
            }

            if (previous) {
                const prevDate = new Date(previous.date + 'T00:00:00Z');
                const currDate = new Date(record.date + 'T00:00:00Z');
                const diffTime = currDate.getTime() - prevDate.getTime();
                const diffDays = Math.round(diffTime / (1000 * 3600 * 24));

                if (diffDays === 1) {
                    const change = Math.abs(temp - previous.temperatureCelsius);
                    if (change > thresholds.maxDelta) {
                        anomalies.push({
                            type: "Sudden temperature change",
                            explanation: `temperature changed by ${change.toFixed(1)}°C since the previous day`
                        });
                    }
                }
            }

            results.push({
                date: record.date,
                temperatureCelsius: temp,
                rainfallMm: rain,
                windSpeedKmh: wind,
                hasAnomalies: anomalies.length > 0,
                status: anomalies.length > 0 ? "Anomaly" : "Normal",
                anomalies: anomalies
            });

            previous = record;
        }

        return results;
    }

    // -------------------------------------------------------------------------
    // Render Functions
    // -------------------------------------------------------------------------

    function renderAll() {
        state.results = evaluateAnomalies(state.records, state.thresholds);

        renderSummary(state.results);
        renderHorizonChart(state.results, state.thresholds);
        renderObservationMatrix(state.results);

        // Compute dominant condition for WebGL ocean color blend
        let heatCount = 0;
        let rainCount = 0;
        let coldCount = 0;
        let stormCount = 0;

        for (const res of state.results) {
            for (const an of res.anomalies) {
                if (an.type.includes('High temperature')) heatCount++;
                if (an.type.includes('Low temperature')) coldCount++;
                if (an.type.includes('Heavy rainfall')) rainCount++;
                if (an.type.includes('Strong wind') || an.type.includes('Sudden temperature')) stormCount++;
            }
        }

        const totalAnomalies = heatCount + rainCount + coldCount + stormCount;
        let condition = 'nominal';
        if (totalAnomalies > 0) {
            if (stormCount >= 2 || (rainCount > 0 && stormCount > 0)) condition = 'storm';
            else if (heatCount > rainCount) condition = 'heat';
            else if (rainCount > 0) condition = 'rain';
            else if (coldCount > 0) condition = 'cold';
        }

        updateAtmosphericState(condition, totalAnomalies > 0);
    }

    function renderSummary(results) {
        const totalDays = results.length;
        let anomalyDays = 0;
        let peakTemp = -999;
        let lowTemp = 999;
        let maxWind = 0;
        let totalRain = 0;

        for (const res of results) {
            if (res.hasAnomalies) anomalyDays++;
            const t = res.temperatureCelsius;
            if (t > peakTemp) peakTemp = t;
            if (t < lowTemp) lowTemp = t;
            if (res.windSpeedKmh > maxWind) maxWind = res.windSpeedKmh;
            totalRain += res.rainfallMm;
        }

        if (totalDays === 0) {
            peakTemp = 0;
            lowTemp = 0;
        }

        elements.statTotalDays.textContent = totalDays;
        elements.statAnomalyDays.textContent = anomalyDays;
        const pct = totalDays > 0 ? Math.round((anomalyDays / totalDays) * 100) : 0;
        elements.statAnomalyPercent.textContent = `${pct}% of Days Flagged`;

        if (anomalyDays > 0) {
            elements.statAnomalyCell.classList.add('alert-cell');
        } else {
            elements.statAnomalyCell.classList.remove('alert-cell');
        }

        elements.statPeakTemp.innerHTML = `${peakTemp.toFixed(1)}<span class="unit">°C</span>`;
        elements.statLowTemp.textContent = `Lowest: ${lowTemp.toFixed(1)}°C`;
        elements.statMaxWind.innerHTML = `${maxWind.toFixed(1)}<span class="unit">km/h</span>`;
        elements.statTotalRain.innerHTML = `${totalRain.toFixed(1)}<span class="unit">mm</span>`;

        elements.matrixCountBadge.textContent = `${totalDays} DAYS LOADED`;
        elements.chartSourceTitle.textContent = state.activeTitle;
    }

    // Interactive SVG Trend Curve & Precipitation Bars
    function renderHorizonChart(results, thresholds) {
        const svg = elements.horizonSvg;
        if (!svg) return;

        const gridLayer = document.getElementById('svg-grid-layer');
        const rainLayer = document.getElementById('svg-rain-layer');
        const thresholdLayer = document.getElementById('svg-threshold-layer');
        const tempAreaLayer = document.getElementById('svg-temp-area-layer');
        const tempLineLayer = document.getElementById('svg-temp-line-layer');
        const pointsLayer = document.getElementById('svg-data-points-layer');
        const windLayer = document.getElementById('svg-wind-layer');

        gridLayer.innerHTML = '';
        rainLayer.innerHTML = '';
        thresholdLayer.innerHTML = '';
        tempAreaLayer.innerHTML = '';
        tempLineLayer.innerHTML = '';
        pointsLayer.innerHTML = '';
        windLayer.innerHTML = '';

        if (!results || results.length === 0) return;

        const width = 900;
        const height = 280;
        const padLeft = 55;
        const padRight = 35;
        const padTop = 30;
        const padBottom = 45;
        const chartW = width - padLeft - padRight;
        const chartH = height - padTop - padBottom;

        let minT = Math.min(...results.map(r => r.temperatureCelsius), thresholds.minTemp);
        let maxT = Math.max(...results.map(r => r.temperatureCelsius), thresholds.maxTemp);
        minT = Math.floor(minT - 3);
        maxT = Math.ceil(maxT + 3);
        const tempRange = Math.max(10, maxT - minT);

        const maxR = Math.max(...results.map(r => r.rainfallMm), thresholds.maxRain, 20);

        function getYForTemp(temp) {
            return padTop + chartH - ((temp - minT) / tempRange) * chartH;
        }

        function getX(idx) {
            if (results.length === 1) return padLeft + chartW * 0.5;
            return padLeft + (idx / (results.length - 1)) * chartW;
        }

        // 1. Grid Lines & Axis Labels
        const gridSteps = 4;
        for (let i = 0; i <= gridSteps; i++) {
            const tVal = minT + (i / gridSteps) * tempRange;
            const y = getYForTemp(tVal);

            const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
            line.setAttribute('x1', padLeft);
            line.setAttribute('y1', y);
            line.setAttribute('x2', width - padRight);
            line.setAttribute('y2', y);
            line.setAttribute('class', 'chart-grid-line');
            gridLayer.appendChild(line);

            const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            text.setAttribute('x', padLeft - 10);
            text.setAttribute('y', y + 4);
            text.setAttribute('text-anchor', 'end');
            text.setAttribute('class', 'chart-axis-label');
            text.textContent = `${Math.round(tVal)}°C`;
            gridLayer.appendChild(text);
        }

        // 2. Threshold Boundary Line
        if (thresholds.maxTemp >= minT && thresholds.maxTemp <= maxT) {
            const yMax = getYForTemp(thresholds.maxTemp);
            const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
            line.setAttribute('x1', padLeft);
            line.setAttribute('y1', yMax);
            line.setAttribute('x2', width - padRight);
            line.setAttribute('y2', yMax);
            line.setAttribute('class', 'chart-threshold-bound');
            thresholdLayer.appendChild(line);

            const label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            label.setAttribute('x', width - padRight - 4);
            label.setAttribute('y', yMax - 6);
            label.setAttribute('text-anchor', 'end');
            label.setAttribute('class', 'chart-axis-label');
            label.setAttribute('fill', '#E76F51');
            label.textContent = `ALERT LIMIT: ${thresholds.maxTemp.toFixed(1)}°C`;
            thresholdLayer.appendChild(label);
        }

        // 3. Rainfall Volume Bars
        const barWidth = Math.min(28, (chartW / results.length) * 0.45);
        results.forEach((res, idx) => {
            const x = getX(idx);
            const rainH = (res.rainfallMm / maxR) * (chartH * 0.65);
            const barY = padTop + chartH - rainH;

            if (res.rainfallMm > 0) {
                const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
                rect.setAttribute('x', x - barWidth / 2);
                rect.setAttribute('y', barY);
                rect.setAttribute('width', barWidth);
                rect.setAttribute('height', Math.max(3, rainH));
                rect.setAttribute('class', 'chart-rain-rect');
                rainLayer.appendChild(rect);

                const rainLabel = document.createElementNS('http://www.w3.org/2000/svg', 'text');
                rainLabel.setAttribute('x', x);
                rainLabel.setAttribute('y', barY - 4);
                rainLabel.setAttribute('text-anchor', 'middle');
                rainLabel.setAttribute('class', 'chart-axis-label');
                rainLabel.setAttribute('fill', '#2EC4B6');
                rainLabel.textContent = `${res.rainfallMm.toFixed(0)}mm`;
                rainLayer.appendChild(rainLabel);
            }

            const dateText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            dateText.setAttribute('x', x);
            dateText.setAttribute('y', height - 12);
            dateText.setAttribute('text-anchor', 'middle');
            dateText.setAttribute('class', 'chart-axis-label');
            dateText.textContent = formatDateShort(res.date);
            gridLayer.appendChild(dateText);
        });

        // 4. Smooth Spline Temperature Area & Line
        const points = results.map((r, i) => ({ x: getX(i), y: getYForTemp(r.temperatureCelsius) }));

        if (points.length > 1) {
            let pathD = `M ${points[0].x} ${points[0].y}`;
            let areaD = `M ${points[0].x} ${padTop + chartH} L ${points[0].x} ${points[0].y}`;

            for (let i = 0; i < points.length - 1; i++) {
                const p0 = points[i === 0 ? 0 : i - 1];
                const p1 = points[i];
                const p2 = points[i + 1];
                const p3 = points[i + 2 >= points.length ? points.length - 1 : i + 2];

                const cp1x = p1.x + (p2.x - p0.x) / 6;
                const cp1y = p1.y + (p2.y - p0.y) / 6;
                const cp2x = p2.x - (p3.x - p1.x) / 6;
                const cp2y = p2.y - (p3.y - p1.y) / 6;

                pathD += ` C ${cp1x.toFixed(1)} ${cp1y.toFixed(1)}, ${cp2x.toFixed(1)} ${cp2y.toFixed(1)}, ${p2.x.toFixed(1)} ${p2.y.toFixed(1)}`;
                areaD += ` C ${cp1x.toFixed(1)} ${cp1y.toFixed(1)}, ${cp2x.toFixed(1)} ${cp2y.toFixed(1)}, ${p2.x.toFixed(1)} ${p2.y.toFixed(1)}`;
            }

            areaD += ` L ${points[points.length - 1].x} ${padTop + chartH} Z`;

            const areaPath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
            areaPath.setAttribute('d', areaD);
            areaPath.setAttribute('fill', 'url(#tempGradient)');
            tempAreaLayer.appendChild(areaPath);

            const linePath = document.createElementNS('http://www.w3.org/2000/svg', 'path');
            linePath.setAttribute('d', pathD);
            linePath.setAttribute('class', 'chart-temp-spline');
            tempLineLayer.appendChild(linePath);
        }

        // 5. Interactive Points & Tooltip
        results.forEach((res, idx) => {
            const x = getX(idx);
            const y = getYForTemp(res.temperatureCelsius);

            const windText = document.createElementNS('http://www.w3.org/2000/svg', 'text');
            windText.setAttribute('x', x);
            windText.setAttribute('y', y - 14);
            windText.setAttribute('text-anchor', 'middle');
            windText.setAttribute('class', 'chart-axis-label');
            windText.setAttribute('font-weight', '600');
            windText.textContent = `${res.windSpeedKmh.toFixed(0)} km/h`;
            windLayer.appendChild(windText);

            const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
            circle.setAttribute('cx', x);
            circle.setAttribute('cy', y);
            circle.setAttribute('r', res.hasAnomalies ? 6 : 4.5);
            circle.setAttribute('class', `chart-data-node ${res.hasAnomalies ? 'anomaly-node' : ''}`);

            circle.addEventListener('mouseenter', (e) => {
                showTooltip(res, e.clientX, e.clientY);
            });
            circle.addEventListener('mousemove', (e) => {
                moveTooltip(e.clientX, e.clientY);
            });
            circle.addEventListener('mouseleave', hideTooltip);

            pointsLayer.appendChild(circle);
        });
    }

    function showTooltip(res, clientX, clientY) {
        const tt = elements.chartTooltip;
        if (!tt) return;

        elements.ttDate.textContent = formatDateLong(res.date);
        elements.ttTemp.textContent = `${res.temperatureCelsius.toFixed(1)}°C`;
        elements.ttRain.textContent = `${res.rainfallMm.toFixed(1)} mm`;
        elements.ttWind.textContent = `${res.windSpeedKmh.toFixed(1)} km/h`;

        if (res.hasAnomalies) {
            elements.ttStatus.textContent = 'Anomaly';
            elements.ttStatus.className = 'tooltip-status';
        } else {
            elements.ttStatus.textContent = 'Normal';
            elements.ttStatus.className = 'tooltip-status nominal';
        }

        tt.classList.remove('hidden');
        moveTooltip(clientX, clientY);
    }

    function moveTooltip(clientX, clientY) {
        const tt = elements.chartTooltip;
        const container = elements.horizonSvg.parentElement;
        if (!tt || !container) return;

        const rect = container.getBoundingClientRect();
        const x = clientX - rect.left;
        const y = clientY - rect.top;

        tt.style.left = `${x}px`;
        tt.style.top = `${y}px`;
    }

    function hideTooltip() {
        if (elements.chartTooltip) elements.chartTooltip.classList.add('hidden');
    }

    // Render Daily Weather Cards
    function renderObservationMatrix(results) {
        const container = elements.observationMatrix;
        if (!container) return;

        container.innerHTML = '';

        if (!results || results.length === 0) {
            container.innerHTML = `
                <div class="matrix-loading-state">
                    <span>No weather records available.</span>
                </div>
            `;
            return;
        }

        results.forEach((res) => {
            const card = document.createElement('article');
            card.className = `observation-row ${res.hasAnomalies ? 'has-anomaly' : ''}`;

            const dateParts = parseDate(res.date);
            const statusClass = res.hasAnomalies ? 'anomaly' : 'nominal';
            const statusText = res.hasAnomalies ? 'Anomaly' : 'Normal';

            let anomalyCalloutHtml = '';
            if (res.hasAnomalies && res.anomalies.length > 0) {
                anomalyCalloutHtml = `
                    <div class="diagnostic-callout" role="alert">
                        ${res.anomalies.map(an => `
                            <div class="diagnostic-item">
                                <span class="diag-bullet">▲</span>
                                <span class="diag-type">${escapeHtml(an.type)}:</span>
                                <span class="diag-explanation">${escapeHtml(an.explanation)}</span>
                            </div>
                        `).join('')}
                    </div>
                `;
            }

            card.innerHTML = `
                <div class="row-primary-data">
                    <div class="date-block">
                        <span class="day-name">${dateParts.weekday}</span>
                        <span class="date-val">${dateParts.fullFormatted}</span>
                    </div>

                    <div class="readouts-cluster">
                        <div class="metric-pill-item">
                            <span class="label">TEMPERATURE</span>
                            <span class="val temp-readout">${res.temperatureCelsius.toFixed(1)}<span class="unit">°C</span></span>
                        </div>
                        <div class="metric-pill-item">
                            <span class="label">RAINFALL</span>
                            <span class="val">${res.rainfallMm.toFixed(1)}<span class="unit">mm</span></span>
                        </div>
                        <div class="metric-pill-item">
                            <span class="label">WIND SPEED</span>
                            <span class="val">${res.windSpeedKmh.toFixed(1)}<span class="unit">km/h</span></span>
                        </div>
                    </div>

                    <div class="status-wrap">
                        <span class="status-pill ${statusClass}">
                            <span class="status-dot"></span>
                            ${statusText}
                        </span>
                    </div>
                </div>
                ${anomalyCalloutHtml}
            `;

            container.appendChild(card);
        });
    }

    // -------------------------------------------------------------------------
    // API Data Fetching & CSV Handling
    // -------------------------------------------------------------------------

    async function fetchForecast(city, apiKey) {
        showLoading(`Fetching forecast for ${city}...`);

        const params = new URLSearchParams({
            city: city.trim(),
            maxTemp: state.thresholds.maxTemp,
            minTemp: state.thresholds.minTemp,
            maxRain: state.thresholds.maxRain,
            maxWind: state.thresholds.maxWind,
            maxDelta: state.thresholds.maxDelta
        });

        if (apiKey && apiKey.trim()) {
            params.set('apiKey', apiKey.trim());
        }

        try {
            const resp = await fetch(`/api/forecast?${params.toString()}`);
            const data = await resp.json();

            if (!resp.ok) {
                throw new Error(data.error || `HTTP ${resp.status}: Could not fetch forecast.`);
            }

            state.activeTitle = data.title || `5-Day Forecast for ${city}`;
            state.records = data.results.map(r => ({
                date: r.date,
                temperatureCelsius: r.temperatureCelsius,
                rainfallMm: r.rainfallMm,
                windSpeedKmh: r.windSpeedKmh
            }));

            if (elements.geoTelemetry) {
                elements.geoTelemetry.textContent = `Forecast: ${city}`;
            }

            renderAll();
            showToast(`Weather forecast loaded for ${city}.`);
        } catch (err) {
            console.error(err);
            showToast(err.message, true);
            renderAll();
        }
    }

    async function loadSampleCsv() {
        showLoading('Loading sample-weather.csv...');

        const params = new URLSearchParams({
            maxTemp: state.thresholds.maxTemp,
            minTemp: state.thresholds.minTemp,
            maxRain: state.thresholds.maxRain,
            maxWind: state.thresholds.maxWind,
            maxDelta: state.thresholds.maxDelta
        });

        try {
            const resp = await fetch(`/api/sample?${params.toString()}`);
            const data = await resp.json();

            if (!resp.ok) {
                throw new Error(data.error || 'Could not load sample CSV.');
            }

            state.activeTitle = 'Sample Weather Dataset (sample-weather.csv)';
            state.records = data.results.map(r => ({
                date: r.date,
                temperatureCelsius: r.temperatureCelsius,
                rainfallMm: r.rainfallMm,
                windSpeedKmh: r.windSpeedKmh
            }));

            if (elements.geoTelemetry) {
                elements.geoTelemetry.textContent = 'Offline sample-weather.csv';
            }

            renderAll();
            showToast('Sample dataset loaded.');
        } catch (err) {
            console.error(err);
            showToast(err.message, true);
            renderAll();
        }
    }

    async function uploadCsvText(csvText) {
        showLoading('Parsing CSV weather data...');

        const params = new URLSearchParams({
            maxTemp: state.thresholds.maxTemp,
            minTemp: state.thresholds.minTemp,
            maxRain: state.thresholds.maxRain,
            maxWind: state.thresholds.maxWind,
            maxDelta: state.thresholds.maxDelta
        });

        try {
            const resp = await fetch(`/api/csv?${params.toString()}`, {
                method: 'POST',
                headers: { 'Content-Type': 'text/plain; charset=UTF-8' },
                body: csvText
            });

            const data = await resp.json();
            if (!resp.ok) {
                throw new Error(data.error || 'Failed to parse CSV.');
            }

            state.activeTitle = 'Custom CSV Dataset';
            state.records = data.results.map(r => ({
                date: r.date,
                temperatureCelsius: r.temperatureCelsius,
                rainfallMm: r.rainfallMm,
                windSpeedKmh: r.windSpeedKmh
            }));

            if (elements.geoTelemetry) {
                elements.geoTelemetry.textContent = `Custom CSV (${state.records.length} days)`;
            }

            renderAll();
            showToast(`Loaded ${state.records.length} weather records from CSV.`);
            closeCsvModal();
        } catch (err) {
            console.error(err);
            showToast(err.message, true);
        }
    }

    function showLoading(msg) {
        if (elements.observationMatrix) {
            elements.observationMatrix.innerHTML = `
                <div class="matrix-loading-state">
                    <div class="spinner"></div>
                    <span>${escapeHtml(msg)}</span>
                </div>
            `;
        }
    }

    // -------------------------------------------------------------------------
    // Event Handlers & Bindings
    // -------------------------------------------------------------------------

    function setupEventListeners() {
        // Mode Switcher Tabs
        elements.modeLive.addEventListener('click', () => {
            state.mode = 'live';
            elements.modeLive.classList.add('active');
            elements.modeLive.setAttribute('aria-selected', 'true');
            elements.modeCsv.classList.remove('active');
            elements.modeCsv.setAttribute('aria-selected', 'false');

            elements.liveControls.classList.remove('hidden');
            elements.csvControls.classList.add('hidden');
        });

        elements.modeCsv.addEventListener('click', () => {
            state.mode = 'csv';
            elements.modeCsv.classList.add('active');
            elements.modeCsv.setAttribute('aria-selected', 'true');
            elements.modeLive.classList.remove('active');
            elements.modeLive.setAttribute('aria-selected', 'false');

            elements.csvControls.classList.remove('hidden');
            elements.liveControls.classList.add('hidden');
        });

        // City Search
        elements.btnFetchForecast.addEventListener('click', () => {
            const city = elements.cityInput.value.trim();
            if (city) {
                state.city = city;
                fetchForecast(state.city, state.apiKey);
            }
        });

        elements.cityInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                elements.btnFetchForecast.click();
            }
        });

        document.querySelectorAll('.chip[data-city]').forEach(chip => {
            chip.addEventListener('click', () => {
                const city = chip.getAttribute('data-city');
                elements.cityInput.value = city;
                state.city = city;
                fetchForecast(state.city, state.apiKey);
            });
        });

        // API Key Toggle & Popover
        elements.btnToggleKey.addEventListener('click', () => {
            const isHidden = elements.keyPopover.classList.contains('hidden');
            if (isHidden) {
                elements.keyPopover.classList.remove('hidden');
                elements.btnToggleKey.setAttribute('aria-expanded', 'true');
                elements.apiKeyInput.value = state.apiKey;
                elements.apiKeyInput.focus();
            } else {
                elements.keyPopover.classList.add('hidden');
                elements.btnToggleKey.setAttribute('aria-expanded', 'false');
            }
        });

        elements.btnSaveKey.addEventListener('click', () => {
            const val = elements.apiKeyInput.value.trim();
            state.apiKey = val;
            if (val) {
                localStorage.setItem('openweather_api_key', val);
                elements.keyStatusText.textContent = `KEY: SAVED (${val.slice(0, 4)}...${val.slice(-4)})`;
                showToast('API key saved.');
            } else {
                localStorage.removeItem('openweather_api_key');
                elements.keyStatusText.textContent = 'API KEY: SERVER DEFAULT';
                showToast('Reset to server default key.');
            }
            elements.keyPopover.classList.add('hidden');
        });

        document.addEventListener('click', (e) => {
            if (elements.keyPopover && !elements.keyPopover.contains(e.target) && !elements.btnToggleKey.contains(e.target)) {
                elements.keyPopover.classList.add('hidden');
                elements.btnToggleKey.setAttribute('aria-expanded', 'false');
            }
        });

        // CSV Quick Button
        elements.btnLoadSample.addEventListener('click', () => {
            loadSampleCsv();
        });

        // CSV File Upload
        elements.csvFileInput.addEventListener('change', (e) => {
            const file = e.target.files[0];
            if (file) {
                const reader = new FileReader();
                reader.onload = (evt) => {
                    uploadCsvText(evt.target.result);
                };
                reader.readAsText(file, 'UTF-8');
            }
        });

        // CSV Paste Modal
        elements.btnOpenPasteModal.addEventListener('click', () => {
            elements.csvModal.classList.remove('hidden');
            elements.csvPasteTextarea.focus();
        });

        elements.btnCloseModal.addEventListener('click', closeCsvModal);
        elements.btnModalCancel.addEventListener('click', closeCsvModal);

        elements.btnModalSubmit.addEventListener('click', () => {
            const text = elements.csvPasteTextarea.value.trim();
            if (text) {
                uploadCsvText(text);
            } else {
                showToast('Please paste CSV data before submitting.', true);
            }
        });

        // Sliders Real-Time Binding
        const bindSlider = (key, sliderEl, labelEl) => {
            sliderEl.addEventListener('input', () => {
                const val = parseFloat(sliderEl.value);
                state.thresholds[key] = val;
                labelEl.textContent = val.toFixed(1);
                renderAll(); // Immediate 0ms re-evaluation
            });
        };

        bindSlider('maxTemp', elements.sliders.maxTemp, elements.sliderValues.maxTemp);
        bindSlider('minTemp', elements.sliders.minTemp, elements.sliderValues.minTemp);
        bindSlider('maxRain', elements.sliders.maxRain, elements.sliderValues.maxRain);
        bindSlider('maxWind', elements.sliders.maxWind, elements.sliderValues.maxWind);
        bindSlider('maxDelta', elements.sliders.maxDelta, elements.sliderValues.maxDelta);

        // Reset Rules
        elements.btnResetRules.addEventListener('click', () => {
            state.thresholds = {
                maxTemp: 40.0,
                minTemp: 0.0,
                maxRain: 100.0,
                maxWind: 80.0,
                maxDelta: 10.0
            };
            elements.sliders.maxTemp.value = 40.0;
            elements.sliderValues.maxTemp.textContent = '40.0';
            elements.sliders.minTemp.value = 0.0;
            elements.sliderValues.minTemp.textContent = '0.0';
            elements.sliders.maxRain.value = 100.0;
            elements.sliderValues.maxRain.textContent = '100.0';
            elements.sliders.maxWind.value = 80.0;
            elements.sliderValues.maxWind.textContent = '80.0';
            elements.sliders.maxDelta.value = 10.0;
            elements.sliderValues.maxDelta.textContent = '10.0';

            renderAll();
            showToast('Thresholds reset to defaults.');
        });

        setInterval(updateUtcClock, 1000);
        updateUtcClock();
    }

    function closeCsvModal() {
        elements.csvModal.classList.add('hidden');
    }

    function updateUtcClock() {
        if (!elements.utcClock) return;
        const now = new Date();
        const y = now.getUTCFullYear();
        const m = String(now.getUTCMonth() + 1).padStart(2, '0');
        const d = String(now.getUTCDate()).padStart(2, '0');
        const hh = String(now.getUTCHours()).padStart(2, '0');
        const mm = String(now.getUTCMinutes()).padStart(2, '0');
        const ss = String(now.getUTCSeconds()).padStart(2, '0');
        elements.utcClock.textContent = `${y}-${m}-${d} ${hh}:${mm}:${ss} UTC`;
    }

    function showToast(message, isError = false) {
        if (!elements.toastBanner || !elements.toastMessage) return;
        elements.toastMessage.textContent = message;
        if (isError) {
            elements.toastBanner.classList.add('error');
        } else {
            elements.toastBanner.classList.remove('error');
        }
        elements.toastBanner.classList.remove('hidden');

        clearTimeout(elements.toastTimeout);
        elements.toastTimeout = setTimeout(() => {
            elements.toastBanner.classList.add('hidden');
        }, 3500);
    }

    // -------------------------------------------------------------------------
    // Date & String Formatting Utilities
    // -------------------------------------------------------------------------

    function parseDate(dateStr) {
        const [y, m, d] = dateStr.split('-').map(Number);
        const dt = new Date(Date.UTC(y, m - 1, d));
        const weekdays = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
        const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

        return {
            weekday: weekdays[dt.getUTCDay()],
            monthName: months[dt.getUTCMonth()],
            day: String(d).padStart(2, '0'),
            year: y,
            fullFormatted: `${months[dt.getUTCMonth()]} ${String(d).padStart(2, '0')}, ${y}`
        };
    }

    function formatDateShort(dateStr) {
        const parts = parseDate(dateStr);
        return `${parts.monthName} ${parts.day}`;
    }

    function formatDateLong(dateStr) {
        const parts = parseDate(dateStr);
        return `${parts.weekday}, ${parts.monthName} ${parts.day}, ${parts.year}`;
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    // -------------------------------------------------------------------------
    // Initialization
    // -------------------------------------------------------------------------
    function init() {
        initWebGL();
        setupEventListeners();

        if (state.apiKey) {
            elements.keyStatusText.textContent = `KEY: SAVED (${state.apiKey.slice(0, 4)}...${state.apiKey.slice(-4)})`;
        }

        loadSampleCsv();
    }

    window.addEventListener('DOMContentLoaded', init);

})();
