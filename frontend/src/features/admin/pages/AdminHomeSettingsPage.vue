<script setup>
import { computed, onMounted, reactive, ref } from 'vue';

import {
	getAdminBlogProfile,
	getAdminHomePageHero,
	getAdminHomePageTopicSection,
	getAdminHomePageTopics,
	updateAdminBlogProfile,
	updateAdminHomePageHero,
	updateAdminHomePageTopics,
	uploadAdminImage,
} from '../api/adminApi';
import AdminPageHeader from '../components/AdminPageHeader.vue';
import { dismissToast, notifyError, notifySuccess } from '../../../shared/toast/toastStore';

const EMPTY_PROFILE = {
	id: 1,
	name: '',
	intro: '',
	job: '',
	avatarImageUrl: '',
	githubUrl: '',
	email: '',
	blogStartedAt: '',
};

const EMPTY_HERO = {
	id: 1,
	subTitle: '',
	title: '',
	intro: '',
	heroImageUrl: '',
};

const EMPTY_TOPIC_SECTION = {
	id: 1,
	title: '',
	intro: '',
	noteBadge: '',
	note: '',
};

const MAX_TOPIC_COUNT = 6;

const profile = reactive({ ...EMPTY_PROFILE });
const hero = reactive({ ...EMPTY_HERO });
const topicSection = reactive({ ...EMPTY_TOPIC_SECTION });
const topics = ref([]);
let topicKeySequence = 0;

const loading = ref(true);
const saving = ref(false);
const loadError = ref('');
const formError = ref('');
const avatarUploadError = ref('');
const heroUploadError = ref('');
const uploadingAvatar = ref(false);
const uploadingHero = ref(false);
const savedSnapshot = ref('');
const saveFailed = ref(false);
let saveErrorToastId = null;

const avatarInput = ref(null);
const heroInput = ref(null);

const currentSnapshot = computed(() => JSON.stringify({
	profile: profileRequest(),
	hero: heroRequest(),
	topicSettings: topicSettingsRequest(),
}));

const hasChanges = computed(() => savedSnapshot.value !== ''
	&& (saveFailed.value || savedSnapshot.value !== currentSnapshot.value));

const busy = computed(() => loading.value
	|| saving.value
	|| uploadingAvatar.value
	|| uploadingHero.value);

const saveLabel = computed(() => (saving.value ? '저장 중' : '변경사항 저장'));

/** 실패도 성공만큼 눈에 띄어야 한다. 인라인 문구는 폼 아래라 화면 밖일 때가 있다 */
function failWith(message) {
	formError.value = message;
	return notifyError(message);
}

function profileRequest(source = profile) {
	return {
		id: source.id,
		name: source.name.trim(),
		intro: source.intro.trim(),
		job: source.job.trim(),
		avatarImageUrl: source.avatarImageUrl?.trim() || null,
		githubUrl: source.githubUrl?.trim() || null,
		email: source.email?.trim() || null,
		blogStartedAt: source.blogStartedAt || null,
	};
}

function heroRequest(source = hero) {
	return {
		id: source.id,
		subTitle: source.subTitle.trim(),
		title: source.title.trim(),
		intro: source.intro.trim(),
		heroImageUrl: source.heroImageUrl?.trim() || null,
	};
}

function topicSettingsRequest(section = topicSection, items = topics.value) {
	return {
		sectionId: section.id,
		title: section.title.trim(),
		intro: section.intro.trim(),
		noteBadge: section.noteBadge.trim(),
		note: section.note.trim(),
		topics: items.map((topic) => ({
			id: topic.id,
			label: topic.label.trim(),
			title: topic.title.trim(),
			description: topic.description.trim(),
			keywords: parseKeywords(topic.keywordsText),
		})),
	};
}

function parseKeywords(value) {
	return [...new Set(value
		.split(',')
		.map((keyword) => keyword.trim())
		.filter(Boolean))];
}

function toEditableTopic(topic = {}) {
	topicKeySequence += 1;

	return {
		key: topic.id ? `topic-${topic.id}` : `new-topic-${topicKeySequence}`,
		id: topic.id ?? null,
		label: topic.label ?? '',
		title: topic.title ?? '',
		description: topic.description ?? '',
		keywordsText: Array.isArray(topic.keywords) ? topic.keywords.join(', ') : '',
	};
}

function replaceTopics(nextTopics) {
	topics.value = nextTopics.map(toEditableTopic);
}

async function loadSettings() {
	loading.value = true;
	loadError.value = '';
	formError.value = '';

	try {
		const [profileResult, heroResult, topicSectionResult, topicsResult] = await Promise.all([
			getAdminBlogProfile(),
			getAdminHomePageHero(),
			getAdminHomePageTopicSection(),
			getAdminHomePageTopics(),
		]);

		Object.assign(profile, EMPTY_PROFILE, profileResult);
		Object.assign(hero, EMPTY_HERO, heroResult);
		Object.assign(topicSection, EMPTY_TOPIC_SECTION, topicSectionResult);
		replaceTopics(topicsResult);
		savedSnapshot.value = currentSnapshot.value;
		saveFailed.value = false;
	} catch (error) {
		console.error(error);
		loadError.value = error.message || '대문 설정을 불러오지 못했습니다.';
	} finally {
		loading.value = false;
	}
}

function validate() {
	if (!profile.name.trim() || !profile.intro.trim() || !profile.job.trim()) {
		return '프로필 이름, 소개와 직업을 입력해 주세요.';
	}

	if (!hero.subTitle.trim() || !hero.title.trim() || !hero.intro.trim()) {
		return '대문 소제목, 제목과 소개를 입력해 주세요.';
	}

	if (!topicSection.title.trim()
		|| !topicSection.intro.trim()
		|| !topicSection.noteBadge.trim()
		|| !topicSection.note.trim()) {
		return '이야기 소개 문구를 모두 입력해 주세요.';
	}

	const incompleteTopic = topics.value.find((topic) => !topic.label.trim()
		|| !topic.title.trim()
		|| !topic.description.trim());

	if (incompleteTopic) {
		return '각 이야기의 분류, 제목과 설명을 모두 입력해 주세요.';
	}

	const tooManyKeywords = topics.value.some((topic) => parseKeywords(topic.keywordsText).length > 6);
	if (tooManyKeywords) {
		return '이야기별 키워드는 최대 6개까지 입력할 수 있습니다.';
	}

	if (profile.blogStartedAt && profile.blogStartedAt > localToday()) {
		return '블로그 시작일은 오늘 이후로 설정할 수 없습니다.';
	}

	return '';
}

function localToday() {
	const today = new Date();
	const year = today.getFullYear();
	const month = String(today.getMonth() + 1).padStart(2, '0');
	const day = String(today.getDate()).padStart(2, '0');

	return `${year}-${month}-${day}`;
}

async function saveSettings() {
	if (busy.value || !hasChanges.value) {
		return;
	}

	formError.value = validate();

	if (formError.value) {
		return;
	}

	if (saveErrorToastId !== null) {
		dismissToast(saveErrorToastId);
		saveErrorToastId = null;
	}

	// 응답을 기다리는 동안의 입력과 구분할 수 있게, 실제로 보낼 값을 먼저 고정한다.
	const submitted = JSON.parse(currentSnapshot.value);
	const topicKeys = topics.value.map((topic) => topic.key);
	saving.value = true;

	try {
		// 한 요청이 실패해도 나머지 저장이 끝나기 전에는 재시도를 허용하지 않는다.
		const results = await Promise.allSettled([
			updateAdminBlogProfile(submitted.profile),
			updateAdminHomePageHero(submitted.hero),
			updateAdminHomePageTopics(submitted.topicSettings),
		]);
		const [profileResult, heroResult, topicResult] = results;
		const saved = JSON.parse(savedSnapshot.value);

		if (profileResult.status === 'fulfilled') {
			saved.profile = profileRequest(profileResult.value);
		}
		if (heroResult.status === 'fulfilled') {
			saved.hero = heroRequest(heroResult.value);
		}
		if (topicResult.status === 'fulfilled') {
			const { section, topics: savedTopics } = topicResult.value;
			saved.topicSettings = topicSettingsRequest(section, savedTopics.map((topic) => ({
				...topic,
				keywordsText: (topic.keywords ?? []).join(', '),
			})));

			// 서버는 요청 순서로 항목을 반환한다. 입력칸을 다시 만들지 않고 ID만 연결한다.
			topicSection.id = section.id;
			savedTopics.forEach((topic, index) => {
				const current = topics.value.find((item) => item.key === topicKeys[index]);
				if (current) {
					current.id = topic.id;
				}
			});
		}
		savedSnapshot.value = JSON.stringify(saved);

		const labels = ['프로필', '대문', '이야기 소개'];
		const failed = results.flatMap((result, index) => result.status === 'rejected'
			? [{ label: labels[index], error: result.reason }]
			: []);
		if (failed.length) {
			throw new Error(`${failed.map((result) => result.label).join(', ')} 저장에 실패했습니다. ${failed[0].error?.message || '다시 저장해 주세요.'}`);
		}

		saveFailed.value = false;
		notifySuccess(hasChanges.value
			? '요청한 내용을 저장했습니다. 추가로 입력한 변경사항은 한 번 더 저장해 주세요.'
			: '대문 설정을 저장했습니다.');
	} catch (error) {
		// 통신 오류는 서버에 반영됐는지 확정할 수 없으므로, 이전 값과 같아도 재저장을 허용한다.
		saveFailed.value = true;
		console.error(error);
		saveErrorToastId = failWith(error.message || '설정을 저장하지 못했습니다.');
	} finally {
		saving.value = false;
	}
}

async function uploadAvatar(event) {
	await uploadImage(event, 'avatar');
}

async function uploadHero(event) {
	await uploadImage(event, 'hero');
}

async function uploadImage(event, target) {
	const file = event.target.files?.[0];

	if (!file) {
		return;
	}

	const uploading = target === 'avatar' ? uploadingAvatar : uploadingHero;
	const errorMessage = target === 'avatar' ? avatarUploadError : heroUploadError;
	uploading.value = true;
	errorMessage.value = '';

	try {
		const image = await uploadAdminImage(file);

		if (!image.url) {
			throw new Error('업로드한 이미지 주소를 받지 못했습니다.');
		}

		if (target === 'avatar') {
			profile.avatarImageUrl = image.url;
		} else {
			hero.heroImageUrl = image.url;
		}
	} catch (error) {
		console.error(error);
		errorMessage.value = error.message || '이미지를 업로드하지 못했습니다.';
		notifyError(errorMessage.value);
	} finally {
		uploading.value = false;
		event.target.value = '';
	}
}

function removeAvatar() {
	profile.avatarImageUrl = '';
	avatarUploadError.value = '';
}

function removeHeroImage() {
	hero.heroImageUrl = '';
	heroUploadError.value = '';
}

function addTopic() {
	if (topics.value.length >= MAX_TOPIC_COUNT) {
		failWith(`이야기는 최대 ${MAX_TOPIC_COUNT}개까지 등록할 수 있습니다.`);
		return;
	}

	formError.value = '';
	topics.value.push(toEditableTopic());
}

function removeTopic(index) {
	topics.value.splice(index, 1);
}

function moveTopic(index, offset) {
	const nextIndex = index + offset;
	if (nextIndex < 0 || nextIndex >= topics.value.length) {
		return;
	}

	const [topic] = topics.value.splice(index, 1);
	topics.value.splice(nextIndex, 0, topic);
}

onMounted(loadSettings);
</script>

<template>
	<div class="admin-home-settings">
		<AdminPageHeader>
			<template #meta>
				<span v-if="hasChanges">저장하지 않은 변경사항이 있습니다.</span>
				<span v-else>공개 대문에 적용된 내용입니다.</span>
			</template>

			<template #actions>
				<button
					class="admin-button admin-button--solid"
					type="button"
					:disabled="busy || !hasChanges"
					@click="saveSettings"
				>
					{{ saveLabel }}
				</button>
			</template>
		</AdminPageHeader>

		<div v-if="loading" class="admin-settings admin-settings--skeleton" aria-busy="true">
			<section v-for="index in 3" :key="index" class="admin-settings__section" aria-hidden="true">
				<header class="admin-settings__section-header">
					<div>
						<span class="ui-skeleton"></span>
						<span class="ui-skeleton"></span>
					</div>
					<span class="ui-skeleton"></span>
				</header>
				<div class="admin-settings__columns">
					<div class="admin-settings__fields">
						<span v-for="field in 3" :key="field" class="ui-skeleton"></span>
					</div>
					<span class="ui-skeleton admin-settings-skeleton__media"></span>
				</div>
			</section>
		</div>

		<div v-else-if="loadError" class="admin-settings__state admin-settings__state--error">
			<p>{{ loadError }}</p>
			<button class="admin-button" type="button" @click="loadSettings">다시 불러오기</button>
		</div>

		<form v-else class="admin-settings" @submit.prevent="saveSettings">
			<section class="admin-settings__section">
				<header class="admin-settings__section-header">
					<div>
						<span>HOME HERO</span>
						<h2>대문</h2>
					</div>
					<span class="admin-settings__counter">{{ hero.intro.length }}자</span>
				</header>

				<div class="admin-settings__columns">
					<div class="admin-settings__fields">
						<label class="admin-field">
							<span class="admin-field__label">소제목</span>
							<input
								v-model="hero.subTitle"
								class="admin-field__input"
								type="text"
								maxlength="255"
								required
							/>
						</label>

						<label class="admin-field">
							<span class="admin-field__label">제목</span>
							<input
								v-model="hero.title"
								class="admin-field__input"
								type="text"
								maxlength="255"
								required
							/>
						</label>

						<label class="admin-field">
							<span class="admin-field__label">소개</span>
							<textarea
								v-model="hero.intro"
								class="admin-field__textarea"
								rows="7"
								required
							></textarea>
						</label>
					</div>

					<div class="admin-settings__media">
						<span class="admin-field__label">대문 이미지</span>
						<div class="admin-settings__hero-preview">
							<img v-if="hero.heroImageUrl" :src="hero.heroImageUrl" alt="현재 대문" />
							<span v-else>등록된 이미지 없음</span>
						</div>

						<div class="admin-settings__media-actions">
							<button class="admin-button" type="button" :disabled="busy" @click="heroInput?.click()">
								{{ uploadingHero ? '업로드 중' : '이미지 선택' }}
							</button>
							<button
								v-if="hero.heroImageUrl"
								class="admin-button"
								type="button"
								:disabled="busy"
								@click="removeHeroImage"
							>
								이미지 제거
							</button>
						</div>
						<input
							ref="heroInput"
							class="admin-settings__file-input"
							type="file"
							accept="image/jpeg,image/png,image/webp"
							@change="uploadHero"
						/>
						<p v-if="heroUploadError" class="admin-settings__field-error">{{ heroUploadError }}</p>
					</div>
				</div>
			</section>

			<section class="admin-settings__section">
				<header class="admin-settings__section-header">
					<div>
						<span>HOME TOPICS</span>
						<h2>이야기 소개</h2>
					</div>
					<button
						class="admin-button"
						type="button"
						:disabled="busy || topics.length >= MAX_TOPIC_COUNT"
						@click="addTopic"
					>
						항목 추가
					</button>
				</header>

				<div class="admin-settings__topic-copy">
					<label class="admin-field">
						<span class="admin-field__label">영역 제목</span>
						<input
							v-model="topicSection.title"
							class="admin-field__input"
							type="text"
							maxlength="100"
							required
						/>
					</label>

					<label class="admin-field">
						<span class="admin-field__label">영역 소개</span>
						<input
							v-model="topicSection.intro"
							class="admin-field__input"
							type="text"
							maxlength="255"
							required
						/>
					</label>

					<label class="admin-field">
						<span class="admin-field__label">하단 배지</span>
						<input
							v-model="topicSection.noteBadge"
							class="admin-field__input"
							type="text"
							maxlength="50"
							required
						/>
					</label>

					<label class="admin-field">
						<span class="admin-field__label">하단 안내</span>
						<input
							v-model="topicSection.note"
							class="admin-field__input"
							type="text"
							maxlength="255"
							required
						/>
					</label>
				</div>

				<div class="admin-settings__topic-list">
					<div
						v-for="(topic, index) in topics"
						:key="topic.key"
						class="admin-settings__topic-item"
					>
						<div class="admin-settings__topic-item-header">
							<strong>이야기 {{ index + 1 }}</strong>
							<div class="admin-settings__topic-actions">
								<button
									class="admin-icon-button"
									type="button"
									title="위로 이동"
									aria-label="위로 이동"
									:disabled="busy || index === 0"
									@click="moveTopic(index, -1)"
								>
									↑
								</button>
								<button
									class="admin-icon-button"
									type="button"
									title="아래로 이동"
									aria-label="아래로 이동"
									:disabled="busy || index === topics.length - 1"
									@click="moveTopic(index, 1)"
								>
									↓
								</button>
								<button
									class="admin-button admin-button--danger admin-button--small"
									type="button"
									:disabled="busy"
									@click="removeTopic(index)"
								>
									삭제
								</button>
							</div>
						</div>

						<div class="admin-settings__topic-fields">
							<label class="admin-field">
								<span class="admin-field__label">분류</span>
								<input
									v-model="topic.label"
									class="admin-field__input"
									type="text"
									maxlength="50"
									placeholder="개발"
									required
								/>
							</label>

							<label class="admin-field">
								<span class="admin-field__label">제목</span>
								<input
									v-model="topic.title"
									class="admin-field__input"
									type="text"
									maxlength="100"
									placeholder="만들면서 배운 것"
									required
								/>
							</label>

							<label class="admin-field admin-settings__wide-field">
								<span class="admin-field__label">설명</span>
								<textarea
									v-model="topic.description"
									class="admin-field__textarea"
									rows="3"
									maxlength="500"
									required
								></textarea>
							</label>

							<label class="admin-field admin-settings__wide-field">
								<span class="admin-field__label">키워드</span>
								<input
									v-model="topic.keywordsText"
									class="admin-field__input"
									type="text"
									maxlength="255"
									placeholder="사이드 프로젝트, 삽질 기록"
								/>
								<span class="admin-field__hint">쉼표로 구분하며 최대 6개까지 표시됩니다.</span>
							</label>
						</div>
					</div>

					<p v-if="topics.length === 0" class="admin-settings__topic-empty">
						공개할 이야기가 없습니다. 항목을 추가하면 대문에 다시 표시됩니다.
					</p>
				</div>
			</section>

			<section class="admin-settings__section">
				<header class="admin-settings__section-header">
					<div>
						<span>BLOG PROFILE</span>
						<h2>프로필</h2>
					</div>
					<span v-if="profile.blogStartedAt" class="admin-settings__counter">{{ profile.blogStartedAt }} 시작</span>
				</header>

				<div class="admin-settings__columns">
					<div class="admin-settings__fields admin-settings__fields--profile">
						<label class="admin-field">
							<span class="admin-field__label">이름</span>
							<input
								v-model="profile.name"
								class="admin-field__input"
								type="text"
								maxlength="100"
								required
							/>
						</label>

						<label class="admin-field">
							<span class="admin-field__label">직업</span>
							<input
								v-model="profile.job"
								class="admin-field__input"
								type="text"
								maxlength="100"
								required
							/>
						</label>

						<label class="admin-field admin-settings__wide-field">
							<span class="admin-field__label">소개</span>
							<textarea
								v-model="profile.intro"
								class="admin-field__textarea"
								rows="5"
								maxlength="500"
								required
							></textarea>
						</label>

						<label class="admin-field">
							<span class="admin-field__label">블로그 시작일</span>
							<input
								v-model="profile.blogStartedAt"
								class="admin-field__input"
								type="date"
								:max="localToday()"
							/>
						</label>

						<label class="admin-field">
							<span class="admin-field__label">이메일</span>
							<input
								v-model="profile.email"
								class="admin-field__input"
								type="email"
								maxlength="255"
							/>
						</label>

						<label class="admin-field admin-settings__wide-field">
							<span class="admin-field__label">GitHub 주소</span>
							<input
								v-model="profile.githubUrl"
								class="admin-field__input"
								type="url"
								maxlength="500"
							/>
						</label>
					</div>

					<div class="admin-settings__media admin-settings__media--avatar">
						<span class="admin-field__label">프로필 이미지</span>
						<div class="admin-settings__avatar-preview">
							<img v-if="profile.avatarImageUrl" :src="profile.avatarImageUrl" :alt="`${profile.name} 프로필`" />
							<span v-else>{{ profile.name.slice(0, 1) || 'P' }}</span>
						</div>

						<div class="admin-settings__media-actions">
							<button class="admin-button" type="button" :disabled="busy" @click="avatarInput?.click()">
								{{ uploadingAvatar ? '업로드 중' : '이미지 선택' }}
							</button>
							<button
								v-if="profile.avatarImageUrl"
								class="admin-button"
								type="button"
								:disabled="busy"
								@click="removeAvatar"
							>
								이미지 제거
							</button>
						</div>
						<input
							ref="avatarInput"
							class="admin-settings__file-input"
							type="file"
							accept="image/jpeg,image/png,image/webp"
							@change="uploadAvatar"
						/>
						<p v-if="avatarUploadError" class="admin-settings__field-error">{{ avatarUploadError }}</p>
					</div>
				</div>
			</section>

			<p v-if="formError" class="admin-form-error" role="alert">{{ formError }}</p>

			<div class="admin-settings__footer">
				<button
					class="admin-button admin-button--solid"
					type="submit"
					:disabled="busy || !hasChanges"
				>
					{{ saveLabel }}
				</button>
			</div>
		</form>
	</div>
</template>
