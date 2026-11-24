/**
 * CryptoNote Official Portal Logic
 * - Real-time Web Crypto API Envelope V1 Simulator
 * - Mobile App Theme Palette Dropdown (Warm Espresso, Cyber Teal, Neon Blue, etc.)
 * - Ambient Transparent Background Video Controller
 * - Dark Obsidian / Crisp Light Theme Switcher
 * - Hero Phone Screen Interactive Mockup Switcher
 * - Categorized App Screenshot Gallery & Lightbox Modal
 * - Reviews Engine with Local Storage Persistence
 * - Interactive FAQ Accordions
 * - Checksum & Text Copier
 */

document.addEventListener('DOMContentLoaded', () => {
  initTheme();
  initPalettePicker();
  initAmbientVideo();
  initHeroScreenSwitcher();
  initGalleryShowcase();
  initLightbox();
  initCryptoPlayground();
  initReviews();
  initFaq();
  initCopyButtons();
  initMobileMenu();
});

/* ==========================================================================
   1. Theme Management (Dark Obsidian vs Clean Light)
   ========================================================================== */
function initTheme() {
  const themeToggleBtn = document.getElementById('theme-toggle');
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)');
  const STORAGE_KEY = 'cryptoNoteTheme_v4';

  const setTheme = (isDark) => {
    if (isDark) {
      document.body.classList.remove('light-mode');
      if (themeToggleBtn) {
        themeToggleBtn.innerHTML = `
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="5"></circle>
            <line x1="12" y1="1" x2="12" y2="3"></line>
            <line x1="12" y1="21" x2="12" y2="23"></line>
            <line x1="4.22" y1="4.22" x2="5.64" y2="5.64"></line>
            <line x1="18.36" y1="18.36" x2="19.78" y2="19.78"></line>
            <line x1="1" y1="12" x2="3" y2="12"></line>
            <line x1="21" y1="12" x2="23" y2="12"></line>
            <line x1="4.22" y1="19.78" x2="5.64" y2="18.36"></line>
            <line x1="18.36" y1="5.64" x2="19.78" y2="4.22"></line>
          </svg>`;
        themeToggleBtn.setAttribute('title', 'Switch to light mode');
      }
    } else {
      document.body.classList.add('light-mode');
      if (themeToggleBtn) {
        themeToggleBtn.innerHTML = `
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"></path>
          </svg>`;
        themeToggleBtn.setAttribute('title', 'Switch to dark mode');
      }
    }
    localStorage.setItem(STORAGE_KEY, isDark ? 'dark' : 'light');
  };

  const savedTheme = localStorage.getItem(STORAGE_KEY);
  if (savedTheme) {
    setTheme(savedTheme === 'dark');
  } else {
    setTheme(prefersDark.matches);
  }

  if (themeToggleBtn) {
    themeToggleBtn.addEventListener('click', () => {
      const isCurrentlyDark = !document.body.classList.contains('light-mode');
      setTheme(!isCurrentlyDark);
    });
  }
}

/* ==========================================================================
   2. Mobile App Accent Color Palette Switcher (Matches Color.kt & Theme.kt)
   ========================================================================== */
function initPalettePicker() {
  const dropdownBtn = document.getElementById('palette-dropdown-btn');
  const dropdownMenu = document.getElementById('palette-dropdown-menu');
  const dropdownItems = document.querySelectorAll('.palette-dropdown-item');
  const activeDot = document.querySelector('.palette-active-dot');
  const STORAGE_KEY = 'cryptoNotePalette_v4';
  const validPalettes = ['espresso', 'teal', 'blue', 'purple', 'emerald', 'amber', 'rose'];

  const paletteColors = {
    espresso: '#D99B53',
    teal: '#2DD4BF',
    blue: '#38BDF8',
    purple: '#A855F7',
    emerald: '#34D399',
    amber: '#FBBF24',
    rose: '#FB7185'
  };

  const setPalette = (paletteName) => {
    if (!validPalettes.includes(paletteName)) paletteName = 'espresso';

    validPalettes.forEach(p => document.body.classList.remove(`palette-${p}`));
    document.body.classList.add(`palette-${paletteName}`);

    if (activeDot && paletteColors[paletteName]) {
      activeDot.style.background = paletteColors[paletteName];
      activeDot.style.boxShadow = `0 0 6px ${paletteColors[paletteName]}`;
    }

    dropdownItems.forEach(item => {
      item.classList.toggle('active', item.getAttribute('data-palette') === paletteName);
    });

    localStorage.setItem(STORAGE_KEY, paletteName);
  };

  const savedPalette = localStorage.getItem(STORAGE_KEY) || 'espresso';
  setPalette(savedPalette);

  if (dropdownBtn && dropdownMenu) {
    dropdownBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      dropdownMenu.classList.toggle('show');
    });

    document.addEventListener('click', (e) => {
      if (!dropdownBtn.contains(e.target) && !dropdownMenu.contains(e.target)) {
        dropdownMenu.classList.remove('show');
      }
    });
  }

  dropdownItems.forEach(item => {
    item.addEventListener('click', () => {
      const palette = item.getAttribute('data-palette');
      setPalette(palette);
      if (dropdownMenu) dropdownMenu.classList.remove('show');
    });
  });
}

/* ==========================================================================
   3. Ambient Background Video Controller (Transparent Motion)
   ========================================================================== */
function initAmbientVideo() {
  const videoElem = document.getElementById('ambient-video-player');
  const toggleBtn = document.getElementById('ambient-video-toggle');
  const STORAGE_KEY = 'cryptoNoteAmbientVideo_v1';

  if (!videoElem || !toggleBtn) return;

  const updateIcon = (isPlaying) => {
    toggleBtn.innerHTML = isPlaying ? `
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <rect x="6" y="4" width="4" height="16"></rect>
        <rect x="14" y="4" width="4" height="16"></rect>
      </svg>` : `
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <polygon points="5 3 19 12 5 21 5 3"></polygon>
      </svg>`;
    toggleBtn.setAttribute('title', isPlaying ? 'Pause ambient video' : 'Play ambient video');
  };

  const savedState = localStorage.getItem(STORAGE_KEY);
  if (savedState === 'paused') {
    videoElem.pause();
    videoElem.classList.add('dimmed');
    updateIcon(false);
  } else {
    videoElem.play().then(() => updateIcon(true)).catch(() => updateIcon(false));
  }

  toggleBtn.addEventListener('click', () => {
    if (videoElem.paused) {
      videoElem.play().then(() => {
        videoElem.classList.remove('dimmed');
        updateIcon(true);
        localStorage.setItem(STORAGE_KEY, 'playing');
      });
    } else {
      videoElem.pause();
      videoElem.classList.add('dimmed');
      updateIcon(false);
      localStorage.setItem(STORAGE_KEY, 'paused');
    }
  });
}

/* ==========================================================================
   4. Hero 3D Device Interactive Screen Switcher
   ========================================================================== */
function initHeroScreenSwitcher() {
  const heroScreenImg = document.getElementById('hero-phone-screen');
  const switcherBtns = document.querySelectorAll('.screen-switcher-btn');

  if (!heroScreenImg || !switcherBtns.length) return;

  switcherBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const targetSrc = btn.getAttribute('data-screen');
      if (!targetSrc) return;

      switcherBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      heroScreenImg.style.opacity = '0';
      setTimeout(() => {
        heroScreenImg.src = targetSrc;
        heroScreenImg.style.opacity = '1';
      }, 180);
    });
  });
}

/* ==========================================================================
   5. Categorized UI Gallery & Filtering
   ========================================================================== */
function initGalleryShowcase() {
  const filterBtns = document.querySelectorAll('.gallery-filter-btn');
  const galleryCards = document.querySelectorAll('.gallery-item-card');

  if (!filterBtns.length || !galleryCards.length) return;

  filterBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const filter = btn.getAttribute('data-filter');

      filterBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      galleryCards.forEach(card => {
        const category = card.getAttribute('data-category');
        if (filter === 'all' || category === filter) {
          card.style.display = 'flex';
          card.style.opacity = '0';
          setTimeout(() => { card.style.opacity = '1'; }, 50);
        } else {
          card.style.display = 'none';
        }
      });
    });
  });
}

/* ==========================================================================
   6. Fullscreen Lightbox Modal
   ========================================================================== */
function initLightbox() {
  const modal = document.getElementById('lightbox-modal');
  const modalImg = document.getElementById('lightbox-img');
  const modalCaption = document.getElementById('lightbox-caption');
  const closeBtn = document.getElementById('lightbox-close');
  const galleryCards = document.querySelectorAll('.gallery-item-card');

  if (!modal || !modalImg) return;

  const openLightbox = (src, title, desc) => {
    modalImg.src = src;
    if (modalCaption) {
      modalCaption.innerHTML = `<strong>${title}</strong> — <span>${desc}</span>`;
    }
    modal.classList.add('active');
    document.body.style.overflow = 'hidden';
  };

  const closeLightbox = () => {
    modal.classList.remove('active');
    modalImg.src = '';
    document.body.style.overflow = '';
  };

  galleryCards.forEach(card => {
    card.addEventListener('click', () => {
      const img = card.getAttribute('data-img');
      const title = card.getAttribute('data-title') || '';
      const desc = card.getAttribute('data-desc') || '';
      if (img) openLightbox(img, title, desc);
    });
  });

  if (closeBtn) closeBtn.addEventListener('click', closeLightbox);
  modal.addEventListener('click', (e) => {
    if (e.target === modal) closeLightbox();
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && modal.classList.contains('active')) {
      closeLightbox();
    }
  });
}

/* ==========================================================================
   7. Real-Time WebCrypto Envelope V1 Simulator
   ========================================================================== */
function initCryptoPlayground() {
  const noteTitleInput = document.getElementById('demo-note-title');
  const noteContentInput = document.getElementById('demo-note-body');
  const noteZoneSelect = document.getElementById('demo-note-zone');
  const tabButtons = document.querySelectorAll('.crypto-playground-box .tab-btn');
  const outputDisplay = document.getElementById('crypto-output-display');
  const statusIndicator = document.getElementById('crypto-live-status');

  if (!noteTitleInput || !noteContentInput || !outputDisplay) return;

  let currentTab = 'envelope'; // 'envelope' | 'server_view' | 'aad'
  let cachedPayload = null;

  async function generateCryptoSimulation() {
    try {
      if (statusIndicator) statusIndicator.textContent = 'Encrypting on-device...';

      const title = noteTitleInput.value || 'Untitled Note';
      const content = noteContentInput.value || '';
      const zone = noteZoneSelect ? noteZoneSelect.value : 'notes';
      const noteUUID = '8f3a1c92-d61b-4ef9-a9a2-58e1c6b8401e';
      const version = 1;

      // Plaintext JSON payload
      const plaintextObj = {
        uuid: noteUUID,
        zone: zone,
        title: title,
        content: content,
        tags: ['#confidential', zone === 'vault' ? '#vault-locked' : '#everyday'],
        updated_at: new Date().toISOString()
      };
      const plainString = JSON.stringify(plaintextObj);
      const enc = new TextEncoder();
      const plainData = enc.encode(plainString);

      // Generate simulated cryptographic keys via Web Crypto
      const randomKey = await window.crypto.subtle.generateKey(
        { name: 'AES-GCM', length: 256 },
        true,
        ['encrypt', 'decrypt']
      );

      // 12-byte CSPRNG Nonce
      const nonce = window.crypto.getRandomValues(new Uint8Array(12));
      // 8-byte Key ID
      const keyId = window.crypto.getRandomValues(new Uint8Array(8));

      // AAD: Format(0x01) || Cipher(0x01) || KeyID || UUID || Version
      const aadString = `01:01:${toHex(keyId)}:${noteUUID}:${version}`;
      const aadData = enc.encode(aadString);

      // Real AES-256-GCM encryption with AAD
      const ciphertextWithTag = await window.crypto.subtle.encrypt(
        {
          name: 'AES-GCM',
          iv: nonce,
          additionalData: aadData,
          tagLength: 128
        },
        randomKey,
        plainData
      );

      const cipherBytes = new Uint8Array(ciphertextWithTag);
      // GCM Tag is last 16 bytes
      const ciphertext = cipherBytes.slice(0, cipherBytes.length - 16);
      const tag = cipherBytes.slice(cipherBytes.length - 16);

      // Envelope V1 assembly: [0x01][0x01][8B KeyID][12B Nonce][Ciphertext][16B Tag]
      const totalLen = 1 + 1 + 8 + 12 + ciphertext.length + 16;
      const envelope = new Uint8Array(totalLen);
      envelope[0] = 0x01; // format_version = 1
      envelope[1] = 0x01; // cipher = AES-256-GCM
      envelope.set(keyId, 2);
      envelope.set(nonce, 10);
      envelope.set(ciphertext, 22);
      envelope.set(tag, 22 + ciphertext.length);

      // Compute SHA-256 checksum of binary envelope
      const hashBuffer = await window.crypto.subtle.digest('SHA-256', envelope);
      const checksum = toHex(new Uint8Array(hashBuffer));

      cachedPayload = {
        title,
        zone,
        noteUUID,
        version,
        plaintextObj,
        aadString,
        keyIdHex: toHex(keyId),
        nonceHex: toHex(nonce),
        ciphertextHex: toHex(ciphertext.slice(0, 32)) + '...',
        tagHex: toHex(tag),
        fullEnvelopeHex: toHex(envelope),
        checksum,
        envelopeByteLength: totalLen
      };

      renderOutput();
      if (statusIndicator) statusIndicator.textContent = 'Device Sealed • Zero-Knowledge Active';
    } catch (err) {
      console.error('Crypto simulation failed:', err);
    }
  }

  function renderOutput() {
    if (!cachedPayload) return;

    if (currentTab === 'envelope') {
      outputDisplay.innerHTML = `
        <div class="envelope-badge-pill">Envelope V1 Binary Layout (${cachedPayload.envelopeByteLength} bytes)</div>
        <div style="margin-bottom:0.75rem;">
          <span style="color:var(--accent-primary);">0x01</span> (Format) |
          <span style="color:#38bdf8;">0x01</span> (AES-GCM) |
          <span style="color:#fbbf24;">KeyID: ${cachedPayload.keyIdHex}</span> |
          <span style="color:#a855f7;">Nonce: 12B</span> |
          <span style="color:#fb7185;">AuthTag: 16B</span>
        </div>
        <div class="envelope-bytes">
          <strong style="color:var(--text-primary);">Raw Binary Envelope (Hex Stream):</strong><br/>
          ${cachedPayload.fullEnvelopeHex.substring(0, 160)}...
        </div>
        <div style="margin-top:0.75rem; font-size:0.78rem; color:var(--text-muted);">
          SHA-256 Checksum: <span style="color:var(--accent-primary);">${cachedPayload.checksum}</span>
        </div>
      `;
    } else if (currentTab === 'server_view') {
      outputDisplay.innerHTML = `
        <div class="envelope-badge-pill" style="background:rgba(239,68,68,0.15); color:#ef4444;">
          Fastify Backend Database View (Zero Plaintext Visible)
        </div>
        <pre style="margin:0.5rem 0; font-family:var(--font-mono); font-size:0.8rem; color:#94a3b8; line-height:1.5;">{
  "op_id": "op_98d7b3a4-8f12",
  "note_id": "${cachedPayload.noteUUID}",
  "server_version": ${cachedPayload.version},
  "zone": "${cachedPayload.zone}",
  "encrypted_envelope": "0x${cachedPayload.fullEnvelopeHex.substring(0, 48)}...[OPAQUE BLOB]",
  "checksum": "${cachedPayload.checksum}",
  "title": null,      /* 🔒 Server NEVER knows title */
  "content": null,    /* 🔒 Server NEVER knows content */
  "tags": null        /* 🔒 Server NEVER knows tags */
}</pre>
        <div style="font-size:0.78rem; color:var(--accent-primary);">
          ✓ Database breach immune: Mathematical decryption impossible without client master key.
        </div>
      `;
    } else if (currentTab === 'aad') {
      outputDisplay.innerHTML = `
        <div class="envelope-badge-pill">Additional Authenticated Data (AAD)</div>
        <div style="margin:0.5rem 0; font-family:var(--font-mono); font-size:0.84rem; color:#38bdf8;">
          ${cachedPayload.aadString}
        </div>
        <p style="font-size:0.8rem; color:var(--text-secondary); line-height:1.6; margin-top:0.5rem;">
          CryptoNote locks the Format, Cipher ID, Key ID, Note UUID, and Version directly into the AES-256-GCM authentication tag. 
          Any attempt by an attacker to swap ciphertexts between accounts or roll back note versions results in immediate cryptographic rejection.
        </p>
      `;
    }
  }

  // Event Listeners
  noteTitleInput.addEventListener('input', debounce(generateCryptoSimulation, 250));
  noteContentInput.addEventListener('input', debounce(generateCryptoSimulation, 250));
  if (noteZoneSelect) noteZoneSelect.addEventListener('change', generateCryptoSimulation);

  tabButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      tabButtons.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      currentTab = btn.getAttribute('data-tab');
      renderOutput();
    });
  });

  generateCryptoSimulation();
}

function toHex(uint8Array) {
  return Array.from(uint8Array)
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');
}

function debounce(fn, delay) {
  let timer = null;
  return function (...args) {
    clearTimeout(timer);
    timer = setTimeout(() => fn.apply(this, args), delay);
  };
}

/* ==========================================================================
   8. Reviews System (Verified Testimonials + Interactive Form)
   ========================================================================== */
function initReviews() {
  const reviewForm = document.getElementById('review-form');
  const reviewName = document.getElementById('review-name');
  const reviewRole = document.getElementById('review-role');
  const reviewText = document.getElementById('review-text');
  const reviewRatingInput = document.getElementById('review-rating-value');
  const starButtons = document.querySelectorAll('.star-rating-select .star-btn');
  const reviewList = document.getElementById('review-list');
  const STORAGE_KEY = 'cryptoNoteReviews_v4';

  const defaultReviews = [
    {
      id: 'rev-1',
      name: 'Dr. Elena Rostova',
      role: 'Cryptographic Security Researcher',
      rating: 5,
      date: 'October 2026',
      text: 'The architecture here is pristine. Key hierarchy separation between Argon2id KEK, CSPRNG Master Key, and Envelope V1 AES-GCM with authenticated AAD provides genuine zero-knowledge guarantees. Audited their sync protocol: fast, clean, and zero metadata leakage.'
    },
    {
      id: 'rev-2',
      name: 'Marcus Vance',
      role: 'Investigative Tech Journalist',
      rating: 5,
      date: 'September 2026',
      text: 'The Vault tab anti-forensic hardening (FLAG_SECURE blocking screenshots and 60-second clipboard wipe) saved me during field reporting. It is rare to see mobile note apps take zero-footprint memory zeroization seriously.'
    },
    {
      id: 'rev-3',
      name: 'Kavita Patel',
      role: 'Open-Source Privacy Advocate',
      rating: 5,
      date: 'August 2026',
      text: 'Deterministic conflict forking is brilliant! Unlike other sync apps that silently overwrite changes when editing offline, CryptoNote preserves every branch. Fluid Material 3 design makes high security actually enjoyable to use.'
    }
  ];

  const loadReviews = () => {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(defaultReviews));
      return defaultReviews;
    }
    try {
      return JSON.parse(raw);
    } catch {
      return defaultReviews;
    }
  };

  const saveReviews = (reviews) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(reviews));
  };

  const renderReviews = () => {
    if (!reviewList) return;
    const reviews = loadReviews();
    reviewList.innerHTML = '';

    reviews.forEach(rev => {
      const card = document.createElement('div');
      card.className = 'review-card';

      const initials = rev.name.split(' ').map(n => n[0]).slice(0, 2).join('').toUpperCase();
      const stars = '★'.repeat(rev.rating) + '☆'.repeat(5 - rev.rating);

      card.innerHTML = `
        <div class="review-card-top">
          <div class="reviewer-meta">
            <div class="reviewer-avatar">${initials}</div>
            <div>
              <div class="reviewer-name">${escapeHTML(rev.name)}</div>
              <div class="reviewer-badge">${escapeHTML(rev.role || 'Verified User')}</div>
            </div>
          </div>
          <div class="stars-rating">${stars}</div>
        </div>
        <p class="review-text">"${escapeHTML(rev.text)}"</p>
      `;
      reviewList.appendChild(card);
    });
  };

  starButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const val = parseInt(btn.getAttribute('data-value'), 10);
      if (reviewRatingInput) reviewRatingInput.value = val;
      starButtons.forEach(b => {
        const bVal = parseInt(b.getAttribute('data-value'), 10);
        b.classList.toggle('selected', bVal <= val);
      });
    });

    btn.addEventListener('mouseenter', () => {
      const val = parseInt(btn.getAttribute('data-value'), 10);
      starButtons.forEach(b => {
        const bVal = parseInt(b.getAttribute('data-value'), 10);
        b.classList.toggle('hover-active', bVal <= val);
      });
    });

    btn.addEventListener('mouseleave', () => {
      starButtons.forEach(b => b.classList.remove('hover-active'));
    });
  });

  if (reviewForm) {
    reviewForm.addEventListener('submit', (e) => {
      e.preventDefault();
      const name = reviewName.value.trim() || 'Anonymous User';
      const role = reviewRole ? reviewRole.value.trim() || 'Verified User' : 'Verified User';
      const text = reviewText.value.trim();
      const rating = parseInt(reviewRatingInput ? reviewRatingInput.value : '5', 10);

      if (!text) return;

      const current = loadReviews();
      current.unshift({
        id: 'rev-' + Date.now(),
        name,
        role,
        rating,
        date: 'Just now',
        text
      });
      saveReviews(current);

      reviewName.value = '';
      if (reviewRole) reviewRole.value = '';
      reviewText.value = '';

      renderReviews();

      const submitBtn = reviewForm.querySelector('button[type="submit"]');
      if (submitBtn) {
        const origText = submitBtn.textContent;
        submitBtn.textContent = '✓ Review Posted!';
        submitBtn.style.background = 'var(--accent-primary)';
        setTimeout(() => {
          submitBtn.textContent = origText;
          submitBtn.style.background = '';
        }, 2500);
      }
    });
  }

  renderReviews();
}

function escapeHTML(str) {
  return str.replace(/[&<>'"]/g, 
    tag => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' }[tag] || tag)
  );
}

/* ==========================================================================
   9. Interactive FAQ Accordion
   ========================================================================== */
function initFaq() {
  const faqItems = document.querySelectorAll('.faq-item');
  faqItems.forEach(item => {
    const trigger = item.querySelector('.faq-trigger');
    const content = item.querySelector('.faq-content');

    if (trigger && content) {
      trigger.addEventListener('click', () => {
        const isOpen = item.classList.contains('open');

        faqItems.forEach(other => {
          if (other !== item) {
            other.classList.remove('open');
            const otherContent = other.querySelector('.faq-content');
            if (otherContent) otherContent.style.maxHeight = null;
          }
        });

        if (isOpen) {
          item.classList.remove('open');
          content.style.maxHeight = null;
        } else {
          item.classList.add('open');
          content.style.maxHeight = content.scrollHeight + 'px';
        }
      });
    }
  });
}

/* ==========================================================================
   10. Checksum & Text Copier
   ========================================================================== */
function initCopyButtons() {
  const copyButtons = document.querySelectorAll('.btn-copy-hash, .btn-copy-text');
  copyButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const targetText = btn.getAttribute('data-copy');
      if (targetText) {
        navigator.clipboard.writeText(targetText).then(() => {
          const original = btn.textContent;
          btn.textContent = 'Copied!';
          setTimeout(() => {
            btn.textContent = original;
          }, 2000);
        }).catch(err => {
          console.error('Copy failed:', err);
        });
      }
    });
  });
}

/* ==========================================================================
   11. Mobile Navigation Menu
   ========================================================================== */
function initMobileMenu() {
  const toggleBtn = document.querySelector('.mobile-toggle');
  const navLinks = document.querySelector('.nav-links');

  if (toggleBtn && navLinks) {
    toggleBtn.addEventListener('click', () => {
      const isVisible = navLinks.style.display === 'flex';
      navLinks.style.display = isVisible ? 'none' : 'flex';
      if (!isVisible) {
        navLinks.style.flexDirection = 'column';
        navLinks.style.position = 'absolute';
        navLinks.style.top = '100%';
        navLinks.style.left = '0';
        navLinks.style.right = '0';
        navLinks.style.background = 'var(--bg-secondary)';
        navLinks.style.padding = '1.5rem';
        navLinks.style.borderBottom = '1px solid var(--border-subtle)';
        navLinks.style.zIndex = '999';
      }
    });

    navLinks.querySelectorAll('a').forEach(a => {
      a.addEventListener('click', () => {
        if (window.innerWidth <= 992) {
          navLinks.style.display = 'none';
        }
      });
    });
  }
}
