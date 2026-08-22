export async function getApiData(path) {
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
