/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef TNT_FILAMENT_BACKEND_NOOP_PLATFORM_NOOP_H
#define TNT_FILAMENT_BACKEND_NOOP_PLATFORM_NOOP_H

#include <backend/DriverEnums.h>
#include <backend/Platform.h>

namespace filament::backend {

class PlatformNoop final : public Platform {
public:

    int getOSVersion() const noexcept final { return 0; }

    ~PlatformNoop() noexcept override = default;

protected:

    Driver* createDriver(void* sharedContext, const Platform::DriverConfig& driverConfig) noexcept override;
};

} // namespace filament

#endif // TNT_FILAMENT_BACKEND_NOOP_PLATFORM_NOOP_H
