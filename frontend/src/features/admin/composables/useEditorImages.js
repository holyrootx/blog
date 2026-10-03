import { onBeforeUnmount, ref } from 'vue';
import { createBlock } from '../data/postEditorBlocks';
import { uploadAdminImage } from '../api/adminApi';
import { DEFAULT_IMAGE_ALIGN, MAX_IMAGE_WIDTH, MIN_IMAGE_WIDTH, clampImageWidth } from '../../../shared/post/postImageMarkdown';

export function useEditorImages({ blocks, sync }) {
  const resizingId = ref('');
  let stopResize = null;

  // 올리는 중인 블록 id 와 실패 메시지. 블록마다 따로 둔다 — 여러 장을 한꺼번에 놓을 수 있다
  const uploadingIds = ref([]);
  const uploadErrors = ref({});

  // 만들어 둔 임시 주소. 화면을 떠날 때 돌려주지 않으면 그림이 메모리에 계속 남는다
  const objectUrls = [];

  onBeforeUnmount(() => {
    objectUrls.forEach((url) => URL.revokeObjectURL(url));
    stopResize?.();
  });

  function isUploading(block) {
    return uploadingIds.value.includes(block.id);
  }

  /** 파일 고르기 창을 띄우고, 고른 파일을 그 블록에 올린다 */
  function pickImageFor(block) {
    const picker = document.createElement('input');
    picker.type = 'file';
    picker.accept = 'image/*';

    picker.addEventListener('change', () => {
      const [file] = picker.files ?? [];

      if (file) {
        uploadInto(block, file);
      }
    });

    picker.click();
  }

  async function uploadInto(block, file) {
    if (isUploading(block)) {
      return;
    }

    // 고른 파일을 먼저 보여준다. 올리는 데 걸리는 시간만큼 빈 자리를 보고 있을 이유가 없다 —
    // 같은 그림이 이미 이 컴퓨터에 있는데 올렸다가 다시 받아오면 그만큼 더 기다린다
    block.previewUrl = URL.createObjectURL(file);
    objectUrls.push(block.previewUrl);

    // 원본 크기는 파일에만 달린 값이라 업로드 응답을 기다릴 이유가 없다.
    // 서버는 이 값을 모른다 — 알아내게 하려면 이미지를 통째로 메모리에 펼쳐야 한다
    readNaturalSize(block, block.previewUrl);

    uploadingIds.value = [...uploadingIds.value, block.id];
    delete uploadErrors.value[block.id];

    try {
      const image = await uploadAdminImage(file);

      block.url = image.url;
      // 대체 텍스트 기본값을 파일 이름으로 둔다. 비워 두면 화면 낭독기가 읽을 것이 없다
      block.alt = block.alt || image.originalName.replace(/\.[^.]+$/, '');

      sync();
    } catch (error) {
      // 올라가지 않은 그림을 올라간 것처럼 보여주면 안 된다
      block.previewUrl = '';

      // 실패한 블록은 지우지 않는다. 지우면 어디에 무엇을 넣으려 했는지 사라진다
      uploadErrors.value = { ...uploadErrors.value, [block.id]: error.message };
    } finally {
      uploadingIds.value = uploadingIds.value.filter((id) => id !== block.id);
    }
  }

  /**
   * 원본 픽셀 크기를 읽어 블록에 적어 둔다.
   *
   * 저장 형식에 실려 공개 화면이 비율을 미리 알게 되고, 그래야 이미지가 도착할 때
   * 아래 글이 밀리지 않는다. 못 읽으면 값을 비워 둔다 — 크기 정보가 없던
   * 예전 글과 같은 상태이고, 글이 밀릴 뿐 깨지지는 않는다.
   */
  function readNaturalSize(block, source) {
    const probe = new Image();

    probe.addEventListener('load', () => {
      block.naturalWidth = probe.naturalWidth;
      block.naturalHeight = probe.naturalHeight;

      sync();
    });

    probe.src = source;
  }

  /** 이미지 파일 하나를 새 블록으로 만들어 올린다 */
  function insertImage(file, afterIndex) {
    const block = createBlock('image');

    blocks.value.splice(afterIndex + 1, 0, block);
    sync();
    uploadInto(block, file);
  }

  function imageFilesOf(dataTransfer) {
    return [...(dataTransfer?.files ?? [])].filter((file) => file.type.startsWith('image/'));
  }

  /**
   * 붙여넣기로 들어온 이미지.
   *
   * 스크린샷은 대부분 이 경로로 들어온다. 글자 붙여넣기는 건드리지 않는다 —
   * 이미지가 들어 있을 때만 가로챈다.
   */
  function onPaste(block, index, event) {
    const files = imageFilesOf(event.clipboardData);

    if (files.length === 0) {
      return;
    }

    event.preventDefault();
    files.forEach((file, offset) => insertImage(file, index + offset));
  }

  /* ── 이미지 폭 ────────────────────────────── */

  // 이 자리들에는 손이 정확히 멈추지 않아도 붙는다. 눈으로 맞추기 어려운 값들이다
  const SNAP_WIDTHS = [25, 50, 75, MAX_IMAGE_WIDTH];
  const SNAP_RANGE = 3;
  const KEY_STEP = 5;

  /** 0(지정 없음)은 100% 로 그린다 */
  function imageWidthOf(block) {
    return block.width || MAX_IMAGE_WIDTH;
  }

  function imageAlignOf(block) {
    return block.align || DEFAULT_IMAGE_ALIGN;
  }

  function setImageAlign(block, align) {
    if (imageAlignOf(block) === align) {
      return;
    }

    block.align = align;
    sync();
  }

  function setImageWidth(block, value) {
    const snapped = SNAP_WIDTHS.find((target) => Math.abs(target - value) <= SNAP_RANGE) ?? value;
    const next = clampImageWidth(snapped);

    if (next === block.width) {
      return;
    }

    block.width = next;

    // 끄는 동안 수십 번 불리지만 같은 키라 되돌리기 한 칸으로 묶인다
    sync(`width:${block.id}`);
  }

  /**
   * 손잡이를 잡고 끄는 동안 폭을 바꾼다.
   *
   * 가운데 정렬이라 한쪽을 당기면 반대쪽도 같이 좁아진다. 그래서 폭 변화는
   * 커서가 움직인 거리의 두 배다.
   *
   * pointer 이벤트로 처리하고 전파를 끊는다. 블록 순서 바꾸기가 같은 몸짓(누르고 끌기)을
   * 쓰기 때문에, 안 끊으면 손잡이를 당길 때 블록이 통째로 옮겨진다.
   */
  function onResizeStart(block, side, event) {
    event.preventDefault();
    event.stopPropagation();

    const track = event.currentTarget.closest('.block-editor__image');

    if (!track) {
      return;
    }

    const trackWidth = track.getBoundingClientRect().width;

    if (trackWidth <= 0) {
      return;
    }

    const startX = event.clientX;
    const startWidth = imageWidthOf(block);
    const direction = side === 'left' ? -1 : 1;

    resizingId.value = block.id;

    const onMove = (moveEvent) => {
      const moved = (moveEvent.clientX - startX) * direction;

      setImageWidth(block, startWidth + (moved / trackWidth) * 200);
    };

    const onEnd = () => {
      resizingId.value = '';
      stopResize = null;

      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onEnd);
      window.removeEventListener('pointercancel', onEnd);
    };

    // 화면을 떠나는 중에 끌고 있었으면 붙은 채로 남는다
    stopResize = onEnd;

    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onEnd);
    window.addEventListener('pointercancel', onEnd);
  }

  /**
   * 마우스 없이도 폭을 바꿀 수 있어야 한다. 손잡이는 버튼이라 키가 바로 들어온다.
   *
   * 어느 쪽 손잡이를 잡았든 → 가 크게, ← 가 작게다. 왼쪽 손잡이에서 방향을
   * 뒤집으면 "물리적으로는" 맞지만 누르는 사람은 매번 헷갈린다.
   */
  function onResizeKeydown(block, event) {
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      event.preventDefault();

      const step = event.key === 'ArrowRight' ? KEY_STEP : -KEY_STEP;

      setImageWidth(block, imageWidthOf(block) + step);
      return;
    }

    if (event.key === 'Home') {
      event.preventDefault();
      setImageWidth(block, MIN_IMAGE_WIDTH);
      return;
    }

    if (event.key === 'End') {
      event.preventDefault();
      setImageWidth(block, MAX_IMAGE_WIDTH);
    }
  }

  return {
    uploadErrors,
    resizingId,
    isUploading,
    pickImageFor,
    onPaste,
    imageFilesOf,
    insertImage,
    setImageAlign,
    onResizeStart,
    onResizeKeydown,
  };
}
