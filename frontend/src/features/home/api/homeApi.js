const DEFAULT_PROFILE_ID = 1;
const DEFAULT_HOME_PAGE_HERO_ID = 1;

async function getApiData(path) {
  const response = await fetch(path, {
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`API request failed. status=${response.status}, path=${path}`);
  }

  const body = await response.json();

  if (!body.success) {
    throw new Error(`API response failed. code=${body.code}, path=${path}`);
  }

  return body.data;
}

export function getBlogProfile(profileId = DEFAULT_PROFILE_ID) {
  return getApiData(`/api/v1/blog/profile?profileId=${profileId}`);
}

export function getHomePageHero(homePageHeroId = DEFAULT_HOME_PAGE_HERO_ID) {
  return getApiData(`/api/v1/blog/home/hero?homePageHeroId=${homePageHeroId}`);
}
