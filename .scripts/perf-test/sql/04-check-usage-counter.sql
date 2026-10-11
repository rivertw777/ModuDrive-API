-- namespace.used_bytes(카운터)와 실제 파일 크기 합이 어긋난 드라이브 찾기
-- 영구 삭제·commit 경로를 바꾼 뒤 매번 0행이어야 함
select n.id, n.used_bytes, coalesce(s.total, 0) as actual
from namespace n
left join (select namespace_id, sum(file_size) as total from file
           where is_directory = false and status <> 'PENDING' and deleted_at is null
           group by namespace_id) s on s.namespace_id = n.id
where n.used_bytes <> coalesce(s.total, 0);
